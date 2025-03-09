package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlin.time.Duration

/**
 * Abstract class representing a reference to an actor in the system.
 * Contains basic information about the actor and defines methods
 * for interacting with it.
 *
 * @property address The address of the actor.
 */
abstract class ActorRef(
    val address: Address
) {
    /**
     * Send a message to the actor referenced by this `ActorRef`.
     *
     * @param msg The message to be sent to the actor.
     */
    abstract suspend fun tell(msg: Actor.Message)

    /**
     * Sends a message to the actor and waits for a response within the specified timeout.
     *
     * @param msg The message to be sent to the actor.
     * @param timeout The maximum duration to wait for a response from the actor.
     * @return A `Result` containing the actor's response of type [Res], or an error if the operation fails.
     */
    abstract suspend fun <Res : Actor.Message.Response> ask(msg: Actor.Message, timeout: Duration): Result<Res>

    /**
     * Sends a message to an actor and waits for a response within the default timeout.
     *
     * @param msg The message to be sent to the actor.
     * @return A `Result` containing the actor's response of type [Res], or an error if the operation fails.
     */
    suspend fun <Res : Actor.Message.Response> ask(msg: Actor.Message): Result<Res> =
        ask(msg, ActorSystem.conf.actorAskTimeout)

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

    /**
     * Returns the string representation of the actor reference.
     *
     * @return A string that represents the actor reference, typically including its unique address.
     */
    abstract override fun toString(): String
}
