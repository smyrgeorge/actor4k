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
     * Sends a message to the actor associated with this `ActorRef`.
     *
     * @param msg The message to be sent to the actor.
     * @return A `Result` indicating the completion of the operation, either `Unit` on success or an error on failure.
     */
    abstract suspend fun tell(msg: Actor.Message): Result<Unit>

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
     * Retrieves the current status of the actor associated with this `ActorRef`.
     *
     * @return A `Result` containing the actor's status of type [Actor.Status] if the operation is successful,
     * or an error if the status could not be retrieved.
     */
    abstract suspend fun status(): Result<Actor.Status>

    /**
     * Retrieves the statistics of the actor associated with this `ActorRef`.
     *
     * @return A `Result` containing the actor's statistics of type [Actor.Stats] if the operation is successful,
     * or an error if the statistics could not be retrieved.
     */
    abstract suspend fun stats(): Result<Actor.Stats>

    /**
     * Initiates the shutdown process for the actor associated with this `ActorRef`.
     *
     * This method signals the actor to transition into a shutting down state and
     * release its resources. The actor will stop processing messages after this
     * operation is completed.
     *
     * @return A `Result` indicating the completion of the shutdown operation, either `Unit` on success or an error on failure.
     */
    abstract suspend fun shutdown(): Result<Unit>

    /**
     * Returns the string representation of the actor reference.
     *
     * @return A string that represents the actor reference, typically including its unique address.
     */
    abstract override fun toString(): String
}
