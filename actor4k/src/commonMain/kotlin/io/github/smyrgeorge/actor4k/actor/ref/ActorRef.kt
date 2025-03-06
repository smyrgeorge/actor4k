package io.github.smyrgeorge.actor4k.actor.ref

/**
 * Abstract class representing a reference to an actor in the system.
 * Contains basic information about the actor and defines methods
 * for interacting with it.
 *
 * @property address The address of the actor.
 */
abstract class ActorRef(
    open val address: Address
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
}
