package io.github.smyrgeorge.actor4k.examples.raft

import io.microraft.statemachine.StateMachine
import java.util.function.Consumer

open class AtomicRegister : StateMachine {
    override fun runOperation(commitIndex: Long, operation: Any): Any? {
        if (operation is NewTermOperation) {
            return null
        }

        error("Invalid operation: $operation at commit index: $commitIndex")
    }
    
    override fun getNewTermOperation(): Any = NewTermOperation()

    override fun takeSnapshot(commitIndex: Long, snapshotChunkConsumer: Consumer<Any>) {
        throw UnsupportedOperationException()
    }

    override fun installSnapshot(commitIndex: Long, snapshotChunks: MutableList<Any>) {
        throw UnsupportedOperationException()
    }


    private class NewTermOperation
}