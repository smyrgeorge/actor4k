package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.statemachine.StateMachine
import java.io.Serializable
import java.util.function.Consumer


class ClusterRaftStateMachine : StateMachine {

    override fun runOperation(commitIndex: Long, operation: Any): Any? =
        when (val op = operation as Op) {
            LeaderElected -> null
        }

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) =
        TODO("Not yet implemented")

    override fun installSnapshot(commitIndex: Long, snapshotChunks: MutableList<Any>) =
        TODO("Not yet implemented")

    override fun getNewTermOperation() = LeaderElected

    sealed interface Op : Serializable
    data object LeaderElected : Op {
        private fun readResolve(): Any = LeaderElected
    }

    companion object {
    }
}