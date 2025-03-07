package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor

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

    /**
     * Retrieves the current status of the actor associated with this `LocalRef`.
     *
     * @return the current status of the actor.
     */
    abstract suspend fun status(): Actor.Status

    /**
     * Retrieves the statistical data for the actor associated with this `ActorRef`.
     *
     * @return the statistical information of the actor represented as an `Actor.Stats` object.
     */
    abstract suspend fun stats(): Actor.Stats

    /**
     * Shuts down the actor associated with this `LocalRef`.
     *
     * This method retrieves the actor instance from the registry and initiates
     * its shutdown process. Once invoked, the actor transitions to a shutting down
     * state and ceases processing messages. The actor's resources, such as its mailbox,
     * are released during this process.
     */
    abstract suspend fun shutdown()
}
