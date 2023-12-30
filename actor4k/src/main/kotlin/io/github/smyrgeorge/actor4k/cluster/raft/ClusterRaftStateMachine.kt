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

    private val log = KotlinLogging.logger {}

    override fun runOperation(commitIndex: Long, operation: Any) {
        log.debug { "Received ($commitIndex): $operation" }
        when (val op = operation as Operation) {
            Periodic -> Unit
            LeaderElected -> Unit
            is NodeAdded -> ring.add(op.toServerNode())
            is NodeRemoved -> ring.nodes.firstOrNull { it.dc == op.alias }?.let { ring.remove(it) }
        }
        log.info { "New state ($commitIndex): ${ring.nodes.joinToString { it.dc }} (${ring.nodes.size})" }
    }

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) {
        log.debug { "TAKING SNAPSHOT $commitIndex" }
        val snapshot = Snapshot(commitIndex, ring.nodes.map { it.toEndpoint() })
        snapshotChunkConsumer.accept(snapshot)
    }

    override fun installSnapshot(commitIndex: Long, snapshotChunks: List<Any>) {
        log.info { "INSTALLING SNAPSHOT $commitIndex, $snapshotChunks" }
        if (snapshotChunks.isEmpty()) return

        // Empty hash-ring.
        ring.nodes.forEach { ring.remove(it) }

        @Suppress("UNCHECKED_CAST")
        snapshotChunks as List<Snapshot>

        snapshotChunks
            // Find latest.
            .maxBy { it.commitIndex }
            .nodes
            .forEach { ring.add(it.toServerNode()) }
    }

    override fun getNewTermOperation() = LeaderElected

    sealed interface Operation : Serializable
    data object LeaderElected : Operation {
        private fun readResolve(): Any = LeaderElected
    }

    data object Periodic : Operation {
        private fun readResolve(): Any = Periodic
    }

    data class NodeAdded(val alias: String, val host: String, val port: Int) : Operation {
        fun toServerNode(): ServerNode = ServerNode(alias, host, port)
    }

    data class NodeRemoved(val alias: String) : Operation

    private fun ServerNode.toEndpoint() = ClusterRaftEndpoint(dc, ip, port)
    private fun ClusterRaftEndpoint.toServerNode() = ServerNode(alias, host, port)

    private data class Snapshot(
        val commitIndex: Long,
        val nodes: List<ClusterRaftEndpoint>
    ) : Serializable
}