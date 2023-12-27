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
        log.info { "Received ($commitIndex): $operation" }
        when (val op = operation as Operation) {
            LeaderElected -> Unit
            is NodeAdded -> ring.add(op.toServerNode())
        }
        log.info { "Ring: $ring" }
    }

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) =
        error("[takeSnapshot] is not implemented.")

    override fun installSnapshot(commitIndex: Long, snapshotChunks: MutableList<Any>) =
        error("[installSnapshot] is not implemented.")

    override fun getNewTermOperation() = LeaderElected

    sealed interface Operation : Serializable
    data object LeaderElected : Operation {
        private fun readResolve(): Any = LeaderElected
    }

    data class NodeAdded(val alias: String, val host: String, val port: Int) : Operation {
        fun toServerNode(): ServerNode = ServerNode(alias, host, port)
    }
}