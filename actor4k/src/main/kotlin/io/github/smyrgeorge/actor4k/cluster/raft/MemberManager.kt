package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.MembershipChangeMode
import io.microraft.QueryPolicy
import io.microraft.RaftNode
import io.microraft.RaftNodeStatus
import io.scalecube.cluster.Member
import io.scalecube.net.Address
import kotlinx.coroutines.*
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.*

class MemberManager(private val node: Node) {

    private val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ActorSystem.Conf.memberManagerRoundDelay)
                try {
                    // We make changes every round.
                    val self: RaftNode = ActorSystem.cluster.raft

                    if (self.status == RaftNodeStatus.TERMINATED) {
                        log.info { "${node.alias} (self) is in TERMINATED state but still UP, will shutdown." }
                        ActorSystem.Shutdown.shutdown(ActorSystem.Shutdown.Trigger.SELF_ERROR)
                        continue
                    }

                    // Only leader is allowed to trigger the following actions.
                    if (!self.isLeader()) continue

                    // Send an empty message every period.
                    // It seems to help with the stability of the network.
                    self.replicate<Unit>(StateMachine.Operation.Periodic)

                    var member = leaderNotInTheRing()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (leader) will be added to the hash-ring." }
                        val req = StateMachine.Operation.NodeIsJoining(m.alias(), a.host(), a.port())
                        self.replicate<Unit>(req)
                        continue
                    }

                    val nodeReadyToJoin = getPendingNodesSize() == 0 && getHasJoiningNode()
                    if (nodeReadyToJoin) {
                        self.replicate<Unit>(StateMachine.Operation.NodeJoined)
                        continue
                    }

                    val nodeReadyToLeave = getPendingNodesSize() == 0 && getHasLeavingNode()
                    if (nodeReadyToLeave) {
                        self.replicate<Unit>(StateMachine.Operation.NodeLeft)
                        continue
                    }

                    if (getStatus() != StateMachine.Status.OK) continue

                    val serverNode = anyOfflineCommittedInTheRing()
                    if (serverNode != null) {
                        log.info { "$serverNode (follower) will be removed from the hash-ring." }
                        val req = StateMachine.Operation.NodeIsLeaving(serverNode.dc)
                        val res = self.replicate<ServerNode>(req).join().result
                        try {
                            ShardManager.requestLockShardsForLeavingNode(res)
                        } catch (e: Exception) {
                            log.error(e) { "Could not request shard locking. Reason: ${e.message}" }
                        }

                        continue
                    }

                    val endpoint = anyOfflineCommittedNotInTheRing()
                    if (endpoint != null) {
                        log.info { "$endpoint (follower) will be removed." }
                        val mode = MembershipChangeMode.REMOVE_MEMBER
                        val logIndex = self.committedMembers.logIndex
                        self.changeMembership(endpoint, mode, logIndex)
                        continue
                    }

                    member = anyOnlineCommittedNotInTheRing()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (follower) will be added to the hash-ring." }
                        val req = StateMachine.Operation.NodeIsJoining(m.alias(), a.host(), a.port())
                        val res = self.replicate<ServerNode>(req).join().result
                        try {
                            ShardManager.requestLockShardsForJoiningNode(res)
                        } catch (e: Exception) {
                            log.error(e) { "Could not request shard locking. Reason: ${e.message}" }
                        }

                        continue
                    }

                    member = anyOnlineUncommitted()
                    if (member != null) {
                        val (m, a) = member
                        log.info { "${m.alias()} (learner) will be promoted to follower." }
                        @Suppress("NAME_SHADOWING")
                        val endpoint = Endpoint(m.alias(), a.host(), a.port())
                        val mode = MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER
                        val logIndex = self.committedMembers.logIndex
                        self.changeMembership(endpoint, mode, logIndex)
                        continue
                    }

                } catch (e: Exception) {
                    log.warn { e.message }
                }
            }
        }
    }

    private fun leaderNotInTheRing(): Pair<Member, Address>? {
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
        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()
        return ring.nodes.find { m ->
            members.none { m.dc == it.alias() }
                    && self.committedMembers.members.any { m.dc == it.id }
        }
    }

    private fun anyOfflineCommittedNotInTheRing(): Endpoint? {
        val self: RaftNode = ActorSystem.cluster.raft
        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()
        return self.committedMembers.members.find { m ->
            members.none { m.id == it.alias() }
                    && ring.nodes.none { m.id == it.dc }
        } as Endpoint?
    }

    private fun anyOnlineCommittedNotInTheRing(): Pair<Member, Address>? {
        val self: RaftNode = ActorSystem.cluster.raft
        val ring = ActorSystem.cluster.ring
        val members = ActorSystem.cluster.gossip.members()
        return members.find { m ->
            self.committedMembers.members.any { m.alias() == it.id }
                    && ring.nodes.none { m.alias() == it.dc }
        }?.let { it to it.address() }
    }

    private fun anyOnlineUncommitted(): Pair<Member, Address>? {
        val self: RaftNode = ActorSystem.cluster.raft
        val members = ActorSystem.cluster.gossip.members()
        return members.find { m ->
            self.committedMembers.members.none { m.alias() == it.id }
        }?.let { it to it.address() }
    }

    private fun getStatus(): StateMachine.Status {
        val self: RaftNode = ActorSystem.cluster.raft
        return self.query<StateMachine.Status>(
            /* operation = */ StateMachine.Operation.GetStatus,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getPendingNodesSize(): Int {
        val self: RaftNode = ActorSystem.cluster.raft
        return self.query<Int>(
            /* operation = */ StateMachine.Operation.PendingNodesSize,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getHasJoiningNode(): Boolean {
        val self: RaftNode = ActorSystem.cluster.raft
        return self.query<Boolean>(
            /* operation = */ StateMachine.Operation.HasJoiningNode,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getHasLeavingNode(): Boolean {
        val self: RaftNode = ActorSystem.cluster.raft
        return self.query<Boolean>(
            /* operation = */ StateMachine.Operation.HasLeavingNode,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun RaftNode.isLeader(): Boolean =
        term.leaderEndpoint?.id == node.alias
}