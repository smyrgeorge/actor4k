package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftNode
import io.microraft.RaftRole
import io.microraft.report.RaftNodeReport
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull

class ClusterRaftManager {

    private val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(10_000)
                try {
                    val self: RaftNode = ActorSystem.cluster.raft
                    val report: RaftNodeReport = self.report.join().result
//                    log.info { "[${report.role}][${report.status}] committedMembers: ${report.committedMembers.members.size}" }

                    // TODO: Think again about this action in the future.
                    if (report.role == RaftRole.LEADER) {
                        self.replicate<Unit>(ClusterRaftStateMachine.Periodic)
                    }

                    when {
//                        report.role == RaftRole.LEADER
//                                && ActorSystem.cluster.ring.nodes.none { it.dc == node.alias } -> {
//                            val req = ClusterRaftStateMachine.NodeAdded(node.alias, node.host, node.grpcPort)
//                            self.replicate<Unit>(req).join()
//                        }
//
//                        report.role == RaftRole.FOLLOWER
//                                && ActorSystem.cluster.ring.nodes.none { it.dc == node.alias } -> {
//                            val msg = ClusterRaftMessage.RaftFollowerReady(node.alias, node.host, node.grpcPort)
//                            ActorSystem.cluster.broadcast(msg)
//                        }
//
//                        report.role == RaftRole.LEARNER -> {
//                            val msg = ClusterRaftMessage.RaftNewLearner(node.alias, node.host, node.grpcPort)
//                            ActorSystem.cluster.broadcast(msg)
//                        }
                    }
                } catch (e: Exception) {
                    log.error { e.message }
                }
            }
        }
    }

    suspend fun shutdown() {
        // TODO: wait for acceptance from the leader.
        suspend fun informLeaving() {
            log.info { "Informing the others that we are about to leave." }
            val alias = ActorSystem.cluster.node.alias
            val data = MessageHandler.Protocol.RaftFollowerIsLeaving(alias)
            val message = Message.builder().data(data).build()
            ActorSystem.cluster.gossip.spreadGossip(message).awaitFirstOrNull()
        }

        val self: RaftNode = try {
            ActorSystem.cluster.raft
        } catch (e: UninitializedPropertyAccessException) {
            // We never joined the cluster, so we don't have to inform anybody.
            return
        }

        val report: RaftNodeReport = self.report.join().result

        when {
            // Case for the last node left in the cluster (do not inform anyone, just leave).
            self.committedMembers.members.size == 1 -> return
            // Case this node is a leader.
            report.role == RaftRole.LEADER -> {
                val follower = self.committedMembers.members.first { it.id != ActorSystem.cluster.node.alias }
                // Promote follower to leader.
                log.info { "Transferring leadership to ${follower.id}." }
                self.transferLeadership(follower).join().result
                // Now that node became follower, we can leave the cluster normally.
                informLeaving()
            }
            // Case this node is follower/learner.
            report.role == RaftRole.FOLLOWER -> {
                informLeaving()
            }
        }
    }
}