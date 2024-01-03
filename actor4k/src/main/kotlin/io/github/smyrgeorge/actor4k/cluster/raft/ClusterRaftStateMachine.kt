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
    private var leavingNode: ServerNode? = null
    private val waitingShardLockFrom = mutableSetOf<String>()
    private val waitingShardedActorsToFinishFrom = mutableSetOf<String>()

    enum class Status {
        OK,
        A_NODE_IS_JOINING,
        A_NODE_IS_LEAVING
    }

    private val log = KotlinLogging.logger {}

    override fun runOperation(commitIndex: Long, operation: Any): Any {
        // Store the operation result here.
        var result: Any = Unit

        when (val op = operation as Operation) {
            Periodic -> Unit
            LeaderElected -> Unit
            is NodeIsJoining -> run {
                log.info { "Received ($commitIndex): $operation" }
                // Case that we need to add the leader.
                // Is the first node to join the cluster.
                // No shard migration is needed.
                if (ring.nodes.isEmpty()) {
                    ring.add(op.toServerNode())
                    return@run
                }

                status = Status.A_NODE_IS_JOINING
                joiningNode = op.toServerNode()
                waitingShardLockFrom.addAll(ring.nodes.map { it.dc })
                waitingShardedActorsToFinishFrom.addAll(waitingShardLockFrom)
                result = joiningNode as ServerNode
            }

            is NodeIsLeaving -> {
                log.info { "Received ($commitIndex): $operation" }
                status = Status.A_NODE_IS_LEAVING
                leavingNode = ring.nodes.first { it.dc == op.alias }
                waitingShardLockFrom.addAll(ring.nodes.filter { it.dc != op.alias }.map { it.dc })
                waitingShardedActorsToFinishFrom.addAll(waitingShardLockFrom)
                result = leavingNode as ServerNode
            }

            is NodeJoined -> {
                log.info { "Received ($commitIndex): $operation" }
                ring.add(joiningNode!!)
                joiningNode = null
                status = Status.OK
            }

            is NodeLeft -> {
                log.info { "Received ($commitIndex): $operation" }
                ring.remove(leavingNode!!)
                leavingNode = null
                status = Status.OK
            }

            is ShardsLocked -> {
                log.info { "Received ($commitIndex): $operation" }
                waitingShardLockFrom.remove(op.alias)
            }

            is ShardedActorsFinished -> {
                log.info { "Received ($commitIndex): $operation" }
                waitingShardLockFrom.remove(op.alias)
                waitingShardedActorsToFinishFrom.remove(op.alias)
            }

            GetStatus -> result = status
            HasJoiningNode -> result = joiningNode != null
            HasLeavingNode -> result = leavingNode != null
            is PendingNodesSize -> result = waitingShardedActorsToFinishFrom.size
        }

        // Do not log [Periodic] state updates.
        if (operation is Periodic
            || operation is GetStatus
            || operation is HasJoiningNode
            || operation is HasLeavingNode
            || operation is PendingNodesSize
        ) return result

        log.info {
            buildString {
                append("\n")
                append("New cluster state (commitIndex: $commitIndex):")
                append("\n    Status: $status")
                append("\n    Nodes (${ring.nodes.size}):  ${ring.nodes.joinToString { it.dc }}")
                if (status == Status.A_NODE_IS_JOINING) {
                    append("\n        Joining node: $joiningNode")
                }
                if (status == Status.A_NODE_IS_LEAVING) {
                    append("\n        Leaving node: $leavingNode")
                }
                if (status == Status.A_NODE_IS_JOINING || status == Status.A_NODE_IS_LEAVING) {
                    append("\n        Waiting shard lock from: $waitingShardLockFrom")
                    append("\n        Waiting sharded actors to finish from: $waitingShardedActorsToFinishFrom")
                }
            }
        }

        return result
    }

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) {
        log.debug { "TAKING SNAPSHOT $commitIndex" }
        val snapshot = Snapshot(
            commitIndex = commitIndex,
            status = status,
            joiningNode = joiningNode,
            leavingNode = leavingNode,
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
        joiningNode = latest.joiningNode
        leavingNode = latest.leavingNode

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

    data class NodeIsJoining(val alias: String, val host: String, val port: Int) : Operation {
        fun toServerNode(): ServerNode = ServerNode(alias, host, port)
    }

    data object NodeJoined : Operation {
        private fun readResolve(): Any = NodeJoined
    }

    data class NodeIsLeaving(val alias: String) : Operation
    data object NodeLeft : Operation {
        private fun readResolve(): Any = NodeLeft
    }

    data class ShardsLocked(val alias: String) : Operation
    data class ShardedActorsFinished(val alias: String) : Operation

    data object GetStatus : Operation {
        private fun readResolve(): Any = GetStatus
    }

    data object PendingNodesSize : Operation {
        private fun readResolve(): Any = PendingNodesSize
    }

    data object HasJoiningNode : Operation {
        private fun readResolve(): Any = HasJoiningNode
    }

    data object HasLeavingNode : Operation {
        private fun readResolve(): Any = HasLeavingNode
    }

    private fun ServerNode.toEndpoint() = ClusterRaftEndpoint(dc, ip, port)
    private fun ClusterRaftEndpoint.toServerNode() = ServerNode(alias, host, port)

    private data class Snapshot(
        val commitIndex: Long,
        val status: Status,
        val joiningNode: ServerNode?,
        val leavingNode: ServerNode?,
        val nodes: List<ClusterRaftEndpoint>,
    ) : Serializable
}