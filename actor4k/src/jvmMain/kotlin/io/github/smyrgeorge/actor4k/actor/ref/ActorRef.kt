package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.util.java.JRef

/**
 * Abstract class representing a reference to an actor in the system.
 * Contains basic information about the actor and defines methods
 * for interacting with it.
 *
 * @property shard The shard identifier of the actor.
 * @property name The name of the actor.
 * @property key The unique key associated with the actor.
 * @property address The address of the actor.
 */
@Suppress("unused")
abstract class ActorRef(
    open val shard: String,
    open val name: String,
    open val key: String,
    open val address: String
) {
    /**
     * Send a message to the actor referenced by this `ActorRef`.
     *
     * @param msg The message to be sent to the actor.
     */
    abstract suspend fun tell(msg: Any)
    /**
     * Sends a message to the actor and waits for a response.
     *
     * @param msg The message to be sent to the actor.
     * @return The response returned by the actor.
     */
    abstract suspend fun <R> ask(msg: Any): R

    /**
     * Converts this `ActorRef` to a `JRef` for Java interoperability.
     *
     * @return a `JRef` instance that wraps this `ActorRef`, allowing interaction from Java code.
     */
    fun asJava(): JRef = JRef(this)
}
