package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
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
    abstract suspend fun tell(msg: ActorProtocol): Result<Unit>

    /**
     * Sends a message to the actor and awaits a response within a specified timeout.
     *
     * This method is designed for request-response interactions in actor-based messaging systems,
     * allowing a message to be sent and a corresponding response to be awaited synchronously.
     *
     * @param msg The message to be sent. It must extend [ActorProtocol.Message] and specify a response type [R].
     * @param timeout The maximum duration to wait for a response before the method times out.
     * @return A [Result] containing the response of type [R] if the operation is successful; otherwise, an error.
     */
    abstract suspend fun <R, M> ask(msg: M, timeout: Duration): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response

    /**
     * Sends a message to the actor and awaits a response using the default timeout.
     *
     * This method is designed for request-response interactions in actor-based messaging systems,
     * allowing a message to be sent and a corresponding response to be awaited synchronously.
     *
     * @param msg The message to be sent. It must extend [ActorProtocol.Message] and specify a response type [R].
     * @return A [Result] containing the response of type [R] if the operation is successful; otherwise, an error.
     */
    suspend fun <R, M> ask(msg: M): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response =
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
     * This method signals the actor to transition into a shut-down state and
     * release its resources. The actor will stop processing messages after this
     * operation is completed.
     *
     * @return A `Result` indicating the completion of the shutdown operation, either `Unit` on success or an error on failure.
     */
    abstract suspend fun shutdown(): Result<Unit>

    /**
     * Shuts down and discards all the messages in the actor's mailbox.
     *
     * This method signals the actor to transition into a termination state and
     * discard all messages in its mailbox. The actor will stop processing messages
     * after this operation is completed.
     *
     * @return A `Result` indicating the completion of the termination operation, either `Unit` on success or an error on failure.
     */
    abstract suspend fun terminate(): Result<Unit>

    /**
     * Returns the string representation of the actor reference.
     *
     * @return A string that represents the actor reference, typically including its unique address.
     */
    abstract override fun toString(): String
}
