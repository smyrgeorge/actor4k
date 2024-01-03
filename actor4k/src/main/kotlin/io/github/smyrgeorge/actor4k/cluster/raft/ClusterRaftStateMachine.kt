package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.microraft.statemachine.StateMachine
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.io.Serializable
import java.util.function.Consumer


class ClusterRaftStateMachine(
    private val ring: ConsistentHash<ServerNode>,
) : StateMachine {

    private var status = Status.OK

    private var joiningNode: ServerNode? = null
    private val waitingShardLockFrom = mutableSetOf<String>()
    private val waitingShardedActorsToFinishFrom = mutableSetOf<String>()

    enum class Status {
        OK,
        SHARD_MIGRATION
    }

    private val log = KotlinLogging.logger {}

    override fun runOperation(commitIndex: Long, operation: Any): Any {
        // Do not log [Periodic] operations.
        if (operation !is Periodic) {
            log.info { "Received ($commitIndex): $operation" }
        }

        when (val op = operation as Operation) {
            Periodic -> Unit
            LeaderElected -> Unit
            is NodeAdded -> run {
                // Case that we need to add the leader.
                // Is the first node to join the cluster.
                // No shard migration is needed.
                if (ring.nodes.isEmpty()) {
                    ring.add(op.toServerNode())
                    return@run
                }

                status = Status.SHARD_MIGRATION

                joiningNode = op.toServerNode()
                waitingShardLockFrom.addAll(ring.nodes.map { it.dc })
                waitingShardedActorsToFinishFrom.addAll(ring.nodes.map { it.dc })
                return joiningNode as ServerNode
            }

            is ShardsLocked -> {
                waitingShardLockFrom.remove(op.alias)
            }

            is ShardedActorsFinished -> {
                waitingShardLockFrom.remove(op.alias)
                waitingShardedActorsToFinishFrom.remove(op.alias)
                if (waitingShardLockFrom.isEmpty() && waitingShardedActorsToFinishFrom.isEmpty()) {
                    ring.add(joiningNode)
                    joiningNode = null
                    status = Status.OK
                }
            }

            is NodeRemoved -> {
                ring.nodes.firstOrNull { it.dc == op.alias }?.let { ring.remove(it) }
            }

            GetStatus -> return status
        }

        // Do not log [Periodic] state updates.
        if (operation is Periodic) return Unit

        log.info {
            buildString {
                append("\n")
                append("New cluster state (commitIndex: $commitIndex):")
                append("\n    Status: $status")
                append("\n    Nodes (${ring.nodes.size}):  ${ring.nodes.joinToString { it.dc }}")
                if (status == Status.SHARD_MIGRATION) {
                    append("\n        Joining node: $joiningNode")
                    append("\n        Waiting shard lock from: $waitingShardLockFrom")
                    append("\n        Waiting sharded actors to finish from: $waitingShardedActorsToFinishFrom")
                }
            }
        }

        return Unit
    }

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) {
        log.debug { "TAKING SNAPSHOT $commitIndex" }
        val snapshot = Snapshot(
            commitIndex = commitIndex,
            status = status,
            pendingJoinNode = joiningNode,
            nodes = ring.nodes.map { it.toEndpoint() }
        )
        snapshotChunkConsumer.accept(snapshot)
    }

    override fun installSnapshot(commitIndex: Long, snapshotChunks: List<Any>) {
        log.info { "INSTALLING SNAPSHOT $commitIndex, $snapshotChunks" }
        if (snapshotChunks.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        snapshotChunks as List<Snapshot>

        // Find the latest snapshot.
        val latest = snapshotChunks.maxBy { it.commitIndex }

        status = latest.status
        joiningNode = latest.pendingJoinNode

        // Empty hash-ring and then add the nodes from the snapshot.
        ring.nodes.forEach { ring.remove(it) }
        latest.nodes.forEach { ring.add(it.toServerNode()) }
    }

    override fun getNewTermOperation() = LeaderElected

    sealed interface Operation : Serializable
    data object Periodic : Operation {
        private fun readResolve(): Any = Periodic
    }

    data object LeaderElected : Operation {
        private fun readResolve(): Any = LeaderElected
    }

    data class NodeAdded(val alias: String, val host: String, val port: Int) : Operation {
        fun toServerNode(): ServerNode = ServerNode(alias, host, port)
    }

    data class NodeRemoved(val alias: String) : Operation
    data class ShardsLocked(val alias: String) : Operation
    data class ShardedActorsFinished(val alias: String) : Operation

    data object GetStatus : Operation {
        private fun readResolve(): Any = GetStatus
    }

    private fun ServerNode.toEndpoint() = ClusterRaftEndpoint(dc, ip, port)
    private fun ClusterRaftEndpoint.toServerNode() = ServerNode(alias, host, port)

    private data class Snapshot(
        val commitIndex: Long,
        val status: Status,
        val pendingJoinNode: ServerNode?,
        val nodes: List<ClusterRaftEndpoint>,
    ) : Serializable
}