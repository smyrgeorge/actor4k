package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftNode
import io.microraft.RaftRole
import io.microraft.report.RaftNodeReport
import kotlinx.coroutines.*

class ClusterRaftManager(private val node: Node) {

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
        val self: RaftNode = ActorSystem.cluster.raft
        val report: RaftNodeReport = self.report.join().result

        when {
            // Case for the last node left in the cluster (do not inform anyone, just leave).
            self.committedMembers.members.size == 1 -> return
            // Case this node is a leader.
            report.role == RaftRole.LEADER -> {
                self.committedMembers.members
                    .firstOrNull { it.id != ActorSystem.cluster.node.namespace }
                    ?.let {
                        // Promote follower to leader.
                        self.transferLeadership(it).join().result
                        // Now that node became follower can leave the cluster normally.
                        val message = ClusterRaftMessage.RaftFollowerIsLeaving(ActorSystem.cluster.node.alias)
                        ActorSystem.cluster.broadcast(message)
                    }
            }
            // Case this node is follower.
            report.role == RaftRole.FOLLOWER -> {
                val message = ClusterRaftMessage.RaftFollowerIsLeaving(ActorSystem.cluster.node.alias)
                ActorSystem.cluster.broadcast(message)
            }
        }
    }
}