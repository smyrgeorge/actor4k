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

            // Initial delay (until warm up)
            delay(30_000)

            while (true) {

                try {
                    val self: RaftNode = ActorSystem.cluster.raft
                    val report: RaftNodeReport = ActorSystem.cluster.raft.report.join().result
                    log.info { "[${report.role}][${report.status}] committedMembers: ${report.committedMembers.members.size}" }

                    when {
                        report.role == RaftRole.LEADER
                                && ActorSystem.cluster.ring.nodes.none { it.dc == node.alias } -> {
                            val req = ClusterRaftStateMachine.NodeAdded(node.alias, node.host, node.grpcPort)
                            self.replicate<Unit>(req).join()
                        }

                        report.role == RaftRole.FOLLOWER
                                && ActorSystem.cluster.ring.nodes.none { it.dc == node.alias } -> {
                            val msg = ClusterRaftMessage.RaftFollowerReady(node.alias, node.host, node.grpcPort)
                            ActorSystem.cluster.broadcast(msg)
                        }

                        report.role == RaftRole.LEARNER
                                && ActorSystem.cluster.ring.nodes.none { it.dc == node.alias } -> {
                            val msg = ClusterRaftMessage.RaftNewLearner(node.alias, node.host, node.grpcPort)
                            ActorSystem.cluster.broadcast(msg)
                        }
                    }
                } catch (e: Exception) {
                    log.warn(e) { e.message }
                }

                delay(10_000)
            }
        }
    }
}