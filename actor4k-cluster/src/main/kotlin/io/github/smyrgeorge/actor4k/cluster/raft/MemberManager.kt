package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.launch
import io.microraft.MembershipChangeMode
import io.microraft.QueryPolicy
import io.microraft.RaftEndpoint
import io.microraft.RaftNode
import io.microraft.RaftNodeStatus
import io.microraft.model.message.RaftMessage
import io.scalecube.cluster.Member
import io.scalecube.net.Address
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import org.slf4j.LoggerFactory
import java.util.*

class MemberManager(
    private val conf: ClusterImpl.Conf
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }
    private val protocol = Channel<RaftMessage>(capacity = Channel.UNLIMITED)
    private val mail = Channel<MessageHandler.Protocol.Targeted>(capacity = Channel.UNLIMITED)

    init {
        // Start the main loop.
        launch { loop() }
        launch { mail.consumeEach { handle(it) } }
    }

    fun leader(): RaftEndpoint? = cluster.raft.term.leaderEndpoint
    suspend fun send(m: RaftMessage) = protocol.send(m)
    suspend fun send(m: MessageHandler.Protocol.Targeted) = mail.send(m)

    private fun handle(m: MessageHandler.Protocol.Targeted) {
        when (m) {
            is MessageHandler.Protocol.Targeted.ShardsLocked -> shardsLocked(m)
            is MessageHandler.Protocol.Targeted.ShardedActorsFinished -> shardedActorsFinished(m)
            // The following message types are not supposed to be handled here.
            is MessageHandler.Protocol.Targeted.RaftProtocol -> Unit
            is MessageHandler.Protocol.Targeted.ResInitialGroupMembers -> Unit
        }
    }

    private fun shardsLocked(m: MessageHandler.Protocol.Targeted.ShardsLocked) {
        log.info(m.toString())
        val req = StateMachine.Operation.ShardsLocked(m.alias)
        cluster.raft.replicate<Unit>(req)
    }

    private fun shardedActorsFinished(m: MessageHandler.Protocol.Targeted.ShardedActorsFinished) {
        log.info(m.toString())
        val req = StateMachine.Operation.ShardedActorsFinished(m.alias)
        cluster.raft.replicate<Unit>(req)
    }

    private suspend fun loop() {
        while (true) {
            delay(ActorSystem.conf.memberManagerRoundDelay)
            try {
                // We make changes every round.
                val self: RaftNode = cluster.raft

                if (self.status == RaftNodeStatus.TERMINATED) {
                    log.info("${conf.alias} (self) is in TERMINATED state but still UP, will shutdown.")
                    ActorSystem.Shutdown.shutdown(ActorSystem.Shutdown.Trigger.SELF_ERROR)
                    continue
                }

                // Only leader is allowed to trigger the following actions.
                if (!self.isLeader()) continue

                // Send an empty message every period.
                // It seems to help with the stability of the network.
                self.replicate<Unit>(StateMachine.Operation.Periodic)

                var endpoint = leaderIsNotSet()
                if (endpoint != null) {
                    val req = StateMachine.Operation.SetLeader(endpoint)
                    self.replicate<Unit>(req)
                    continue
                }

                var member = leaderNotInTheRing()
                if (member != null) {
                    val (m, a) = member
                    log.info("${m.alias()} (leader) will be added to the hash-ring.")
                    val req = StateMachine.Operation.LeaderJoined(m.alias(), a.host(), a.port())
                    self.replicate<Unit>(req)
                    continue
                }

                val nodeReadyToJoin = getNodesStillMigratingShardsSize() == 0 && getHasJoiningNode()
                if (nodeReadyToJoin) {
                    self.replicate<Unit>(StateMachine.Operation.NodeJoined)
                    continue
                }

                val nodeReadyToLeave = getNodesStillMigratingShardsSize() == 0 && getHasLeavingNode()
                if (nodeReadyToLeave) {
                    self.replicate<Unit>(StateMachine.Operation.NodeLeft)
                    continue
                }

                val readyToUnlockShards = readyToUnlockShards()
                if (readyToUnlockShards) {
                    self.replicate<Unit>(StateMachine.Operation.ShardMigrationCompleted)
                    try {
                        cluster.shardManager.requestUnlockAShards()
                    } catch (e: Exception) {
                        log.error("Could not request unlock shards. Reason: ${e.message}", e)
                    }
                    continue
                }

                if (getStatus() != StateMachine.Status.OK) continue

                val serverNode = anyOfflineCommittedInTheRing()
                if (serverNode != null) {
                    log.info("$serverNode (follower) will start leave flow.")
                    val req = StateMachine.Operation.NodeIsLeaving(serverNode.dc)
                    val res = self.replicate<ServerNode>(req).join().result
                    try {
                        cluster.shardManager.requestLockShardsForLeavingNode(res)
                    } catch (e: Exception) {
                        log.error("Could not request lock shards. Reason: ${e.message}", e)
                    }
                    continue
                }

                endpoint = anyOfflineCommittedNotInTheRing()
                if (endpoint != null) {
                    log.info("$endpoint (follower) will be removed.")
                    val mode = MembershipChangeMode.REMOVE_MEMBER
                    val logIndex = self.committedMembers.logIndex
                    self.changeMembership(endpoint, mode, logIndex)
                    continue
                }

                member = anyOnlineCommittedNotInTheRing()
                if (member != null) {
                    val (m, a) = member
                    log.info("${m.alias()} (follower) will start join flow.")
                    val req = StateMachine.Operation.NodeIsJoining(m.alias(), a.host(), a.port())
                    val res = self.replicate<ServerNode>(req).join().result
                    try {
                        cluster.shardManager.requestLockShardsForJoiningNode(res)
                    } catch (e: Exception) {
                        log.error("Could not request shard locking. Reason: ${e.message}", e)
                    }
                    continue
                }

                member = anyOnlineUncommitted()
                if (member != null) {
                    val (m, a) = member
                    log.info("${m.alias()} (learner) will be promoted to follower.")
                    @Suppress("NAME_SHADOWING")
                    val endpoint = Endpoint(m.alias(), a.host(), a.port())
                    val mode = MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER
                    val logIndex = self.committedMembers.logIndex
                    self.changeMembership(endpoint, mode, logIndex)
                    continue
                }

            } catch (e: Exception) {
                log.error(e.message)
            }
        }
    }

    private fun leaderIsNotSet(): Endpoint? {
        val self: RaftNode = cluster.raft

        @Suppress("InconsistentCommentForJavaParameter")
        val current = self.query<Endpoint>(
            /* operation = */ StateMachine.Operation.GetLeader,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
        val actual = self.term.leaderEndpoint as Endpoint
        return if (current == actual) null else actual
    }

    private fun leaderNotInTheRing(): Pair<Member, Address>? {
        val ring = cluster.ring
        val members = cluster.gossip.members()
        if (ring.nodes.none { it.dc == conf.alias }) {
            val member = members.first { it.alias() == conf.alias }
            return member to member.address()
        }
        return null
    }

    private fun readyToUnlockShards(): Boolean {
        val status = getStatus()
        val pending = getNodesStillMigratingShardsSize()
        return status.isMigration && pending == 0
    }

    private fun anyOfflineCommittedInTheRing(): ServerNode? {
        val self: RaftNode = cluster.raft
        val ring = cluster.ring
        val members = cluster.gossip.members()
        return ring.nodes.find { m ->
            members.none { m.dc == it.alias() }
                    && self.committedMembers.members.any { m.dc == it.id }
        }
    }

    private fun anyOfflineCommittedNotInTheRing(): Endpoint? {
        val self: RaftNode = cluster.raft
        val ring = cluster.ring
        val members = cluster.gossip.members()
        return self.committedMembers.members.find { m ->
            members.none { m.id == it.alias() }
                    && ring.nodes.none { m.id == it.dc }
        } as Endpoint?
    }

    private fun anyOnlineCommittedNotInTheRing(): Pair<Member, Address>? {
        val self: RaftNode = cluster.raft
        val ring = cluster.ring
        val members = cluster.gossip.members()
        return members.find { m ->
            self.committedMembers.members.any { m.alias() == it.id }
                    && ring.nodes.none { m.alias() == it.dc }
        }?.let { it to it.address() }
    }

    private fun anyOnlineUncommitted(): Pair<Member, Address>? {
        val self: RaftNode = cluster.raft
        val members = cluster.gossip.members()
        return members.find { m ->
            self.committedMembers.members.none { m.alias() == it.id }
        }?.let { it to it.address() }
    }

    private fun getStatus(): StateMachine.Status {
        val self: RaftNode = cluster.raft
        @Suppress("InconsistentCommentForJavaParameter")
        return self.query<StateMachine.Status>(
            /* operation = */ StateMachine.Operation.GetStatus,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getNodesStillMigratingShardsSize(): Int {
        val self: RaftNode = cluster.raft
        @Suppress("InconsistentCommentForJavaParameter")
        return self.query<Int>(
            /* operation = */ StateMachine.Operation.GetNodesStillMigratingShardsSize,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getHasJoiningNode(): Boolean {
        val self: RaftNode = cluster.raft
        @Suppress("InconsistentCommentForJavaParameter")
        return self.query<Boolean>(
            /* operation = */ StateMachine.Operation.HasJoiningNode,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun getHasLeavingNode(): Boolean {
        val self: RaftNode = cluster.raft
        @Suppress("InconsistentCommentForJavaParameter")
        return self.query<Boolean>(
            /* operation = */ StateMachine.Operation.HasLeavingNode,
            /* queryPolicy = */ QueryPolicy.EVENTUAL_CONSISTENCY,
            /* minCommitIndex = */ Optional.empty(),
            /* timeout = */ Optional.empty()
        ).join().result
    }

    private fun RaftNode.isLeader(): Boolean =
        term.leaderEndpoint?.id == conf.alias
}
