package io.github.smyrgeorge.actor4k.examples.raft

import io.microraft.statemachine.StateMachine


class OperableAtomicRegister : AtomicRegister(), StateMachine {

    private var value: Any? = null

    override fun runOperation(commitIndex: Long, operation: Any): Any? {
        when (operation) {
            is SetOperation -> {
                val prev = value
                value = operation.value
                return prev
            }

            is CasOperation -> {
                if (value == operation.currentValue) {
                    value = operation.newValue
                    return true
                }

                return false
            }

            is GetOperation -> return value
            else -> {
                return super.runOperation(commitIndex, operation)
            }
        }
    }

    sealed interface AtomicRegisterOperation
    private data object GetOperation : AtomicRegisterOperation
    private data class SetOperation(val value: Any) : AtomicRegisterOperation
    private data class CasOperation(val currentValue: Any, val newValue: Any) : AtomicRegisterOperation

    companion object {
        fun newSetOperation(value: String): AtomicRegisterOperation = SetOperation(value)
        fun newGetOperation(): AtomicRegisterOperation = GetOperation
        fun newCasOperation(currentValue: Any, newValue: Any): AtomicRegisterOperation =
            CasOperation(currentValue, newValue)
    }
}