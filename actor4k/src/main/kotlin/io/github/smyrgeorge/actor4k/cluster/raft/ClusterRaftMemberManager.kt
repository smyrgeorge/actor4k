package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.MembershipChangeMode
import io.microraft.RaftNode
import io.microraft.RaftRole
import io.microraft.report.RaftNodeReport
import io.scalecube.cluster.Member
import io.scalecube.net.Address
import kotlinx.coroutines.*
import org.ishugaliy.allgood.consistent.hash.node.ServerNode

class ClusterRaftMemberManager(
    private val node: Node
) {

    private val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(10_000)
                try {
                    val self: RaftNode = ActorSystem.cluster.raft
                    val report: RaftNodeReport = self.report.join().result

                    // TODO: Think again about this action in the future.
                    if (report.role == RaftRole.LEADER) {
                        self.replicate<Unit>(ClusterRaftStateMachine.Periodic)
                    }

                    // We make changes every round.
                    var member = leaderNotInTheRing()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (leader) will be added to the hash-ring." }
                        val req = ClusterRaftStateMachine.NodeAdded(m.alias(), a.host(), a.port())
                        self.replicate<Unit>(req).join()
                        continue
                    }

                    val serverNode = anyOfflineCommittedInTheRing()
                    if (serverNode != null) {
                        log.info { "$serverNode (follower) will be removed from the hash-ring." }
                        val req = ClusterRaftStateMachine.NodeRemoved(serverNode.dc)
                        self.replicate<Unit>(req).join()
                        continue
                    }

                    val endpoint = anyOfflineCommittedNotInTheRing()
                    if (endpoint != null) {
                        log.info { "$endpoint (follower) will be removed." }
                        val mode = MembershipChangeMode.REMOVE_MEMBER
                        val logIndex = self.committedMembers.logIndex
                        self.changeMembership(endpoint, mode, logIndex).join()
                        continue
                    }

                    member = anyOnlineCommittedNotInTheRing()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (follower) will be added to the hash-ring." }
                        val req = ClusterRaftStateMachine.NodeAdded(m.alias(), a.host(), a.port())
                        self.replicate<Unit>(req).join()
                        continue
                    }

                    member = anyOnlineUncommitted()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (learner) will be promoted to follower." }
                        @Suppress("NAME_SHADOWING")
                        val endpoint = ClusterRaftEndpoint(m.alias(), a.host(), a.port())
                        val mode = MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER
                        val logIndex = self.committedMembers.logIndex
                        self.changeMembership(endpoint, mode, logIndex).join()
                        continue
                    }

                } catch (e: Exception) {
                    log.warn { e.message }
                }
            }
        }
    }

    private fun leaderNotInTheRing(): Pair<Member, Address>? {
        val self: RaftNode = ActorSystem.cluster.raft
        if (self.report.join().result.role != RaftRole.LEADER) return null

        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()

        if (ring.nodes.none { it.dc == node.alias }) {
            val member = members.first { it.alias() == node.alias }
            return member to member.address()
        }

        return null
    }

    private fun anyOfflineCommittedInTheRing(): ServerNode? {
        val self: RaftNode = ActorSystem.cluster.raft
        if (self.report.join().result.role != RaftRole.LEADER) return null

        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()

        return ring.nodes.find { m ->
            members.none { m.dc == it.alias() }
                    && self.committedMembers.members.any { m.dc == it.id }
        }
    }

    private fun anyOfflineCommittedNotInTheRing(): ClusterRaftEndpoint? {
        val self: RaftNode = ActorSystem.cluster.raft
        if (self.report.join().result.role != RaftRole.LEADER) return null

        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()

        return self.committedMembers.members.find { m ->
            members.none { m.id == it.alias() }
                    && ring.nodes.none { m.id == it.dc }
        } as ClusterRaftEndpoint?
    }

    private fun anyOnlineCommittedNotInTheRing(): Pair<Member, Address>? {
        val self: RaftNode = ActorSystem.cluster.raft
        if (self.report.join().result.role != RaftRole.LEADER) return null

        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()

        return members.find { m ->
            self.committedMembers.members.any { m.alias() == it.id }
                    && ring.nodes.none { m.alias() == it.dc }
        }?.let { it to it.address() }
    }

    private fun anyOnlineUncommitted(): Pair<Member, Address>? {
        val self: RaftNode = ActorSystem.cluster.raft
        if (self.report.join().result.role != RaftRole.LEADER) return null

        val members = ActorSystem.cluster.gossip.members()

        return members.find { m ->
            self.committedMembers.members.none { m.alias() == it.id }
        }?.let { it to it.address() }
    }
}