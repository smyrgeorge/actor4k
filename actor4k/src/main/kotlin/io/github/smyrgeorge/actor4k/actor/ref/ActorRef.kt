package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.util.java.JRef

abstract class ActorRef(
    open val shard: String,
    open val name: String,
    open val key: String,
    open val address: String
) {
    abstract suspend fun tell(msg: Any)
    abstract suspend fun <R> ask(msg: Any): R

    fun asJava(): JRef = JRef(this)
}
