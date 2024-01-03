package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.io.Serializable
import java.util.function.Consumer
import io.microraft.statemachine.StateMachine as RaftStateMachine

class StateMachine(private val ring: ConsistentHash<ServerNode>) : RaftStateMachine {

    private var status = Status.OK
    private var leader = Endpoint("Initial", "Initial", 0)

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
            Operation.Periodic -> Unit
            Operation.LeaderElected -> Unit
            is Operation.SetLeader -> {
                log.info { "Received ($commitIndex): $operation" }
                leader = op.endpoint
            }

            is Operation.NodeIsJoining -> run {
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

            is Operation.NodeIsLeaving -> {
                log.info { "Received ($commitIndex): $operation" }
                status = Status.A_NODE_IS_LEAVING
                leavingNode = ring.nodes.first { it.dc == op.alias }
                waitingShardLockFrom.addAll(ring.nodes.filter { it.dc != op.alias }.map { it.dc })
                waitingShardedActorsToFinishFrom.addAll(waitingShardLockFrom)
                result = leavingNode as ServerNode
            }

            is Operation.NodeJoined -> {
                log.info { "Received ($commitIndex): $operation" }
                ring.add(joiningNode!!)
                joiningNode = null
                status = Status.OK
            }

            is Operation.NodeLeft -> {
                log.info { "Received ($commitIndex): $operation" }
                ring.remove(leavingNode!!)
                leavingNode = null
                status = Status.OK
            }

            is Operation.ShardsLocked -> {
                log.info { "Received ($commitIndex): $operation" }
                waitingShardLockFrom.remove(op.alias)
            }

            is Operation.ShardedActorsFinished -> {
                log.info { "Received ($commitIndex): $operation" }
                waitingShardLockFrom.remove(op.alias)
                waitingShardedActorsToFinishFrom.remove(op.alias)
            }

            Operation.GetStatus -> result = status
            Operation.GetLeader -> result = leader
            Operation.HasJoiningNode -> result = joiningNode != null
            Operation.HasLeavingNode -> result = leavingNode != null
            Operation.PendingNodesSize -> result = waitingShardedActorsToFinishFrom.size
        }

        // We do not log all the operations to the state.
        if (operation.doNotLog) return result

        log.info {
            buildString {
                append("\n")
                append("New cluster state (commitIndex: $commitIndex):")
                append("\n    Status: $status")
                append("\n    Leader: ${leader.alias}")
                append("\n    Nodes (${ring.nodes.size}):  ${ring.nodes.joinToString { it.dc }}")
                if (status == Status.A_NODE_IS_JOINING) {
                    append("\n        Joining node: $joiningNode")
                }
                if (status == Status.A_NODE_IS_LEAVING) {
                    append("\n        Leaving node: $leavingNode")
                }
                if (status == Status.A_NODE_IS_JOINING || status == Status.A_NODE_IS_LEAVING) {
                    append("\n        Waiting shard locks from: $waitingShardLockFrom")
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
            leader = leader,
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
        leader = latest.leader
        joiningNode = latest.joiningNode
        leavingNode = latest.leavingNode

        // Empty hash-ring and then add the nodes from the snapshot.
        ring.nodes.forEach { ring.remove(it) }
        latest.nodes.forEach { ring.add(it.toServerNode()) }
    }

    override fun getNewTermOperation() = Operation.LeaderElected

    sealed interface Operation : Serializable {
        val doNotLog: Boolean

        data object Periodic : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = Periodic
        }

        data object LeaderElected : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = LeaderElected
        }

        data class SetLeader(val endpoint: Endpoint) : Operation {
            override val doNotLog: Boolean = false
        }

        data class NodeIsJoining(val alias: String, val host: String, val port: Int) : Operation {
            override val doNotLog: Boolean = false
            fun toServerNode(): ServerNode = ServerNode(alias, host, port)
        }

        data object NodeJoined : Operation {
            override val doNotLog: Boolean = false
            private fun readResolve(): Any = NodeJoined
        }

        data class NodeIsLeaving(val alias: String) : Operation {
            override val doNotLog: Boolean = false
        }

        data object NodeLeft : Operation {
            override val doNotLog: Boolean = false
            private fun readResolve(): Any = NodeLeft
        }

        data class ShardsLocked(val alias: String) : Operation {
            override val doNotLog: Boolean = false
        }

        data class ShardedActorsFinished(val alias: String) : Operation {
            override val doNotLog: Boolean = false
        }

        data object GetStatus : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = GetStatus
        }

        data object GetLeader : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = GetLeader
        }

        data object PendingNodesSize : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = PendingNodesSize
        }

        data object HasJoiningNode : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = HasJoiningNode
        }

        data object HasLeavingNode : Operation {
            override val doNotLog: Boolean = true
            private fun readResolve(): Any = HasLeavingNode
        }
    }

    private fun ServerNode.toEndpoint() = Endpoint(dc, ip, port)
    private fun Endpoint.toServerNode() = ServerNode(alias, host, port)

    private data class Snapshot(
        val commitIndex: Long,
        val status: Status,
        val leader: Endpoint,
        val joiningNode: ServerNode?,
        val leavingNode: ServerNode?,
        val nodes: List<Endpoint>,
    ) : Serializable
}