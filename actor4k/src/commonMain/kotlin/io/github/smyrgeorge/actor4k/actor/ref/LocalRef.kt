package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import kotlin.time.Duration

/**
 * Represents a local reference to an actor within the actor system. This class serves as
 * a concrete implementation of an `ActorRef` specific to actors located on the same node
 * within the system.
 *
 * A `LocalRef` provides methods to interact with the associated actor, including sending
 * messages, requesting responses, querying its status, and managing its lifecycle.
 *
 * @constructor Creates a new `LocalRef` instance.
 * @param address The unique `Address` of the actor in the actor system.
 * @param actor The actor instance to be referenced.
 */
class LocalRef : ActorRef {

    val clazz: AnyActorClass
    private var actor: AnyActor?

    /**
     * Creates a new `LocalRef` instance.
     *
     * @param address The unique `Address` of the actor in the actor system.
     * @param actor The actor instance to be referenced.
     */
    internal constructor(address: Address, actor: AnyActor) : super(address) {
        this.clazz = actor::class
        this.actor = actor
    }

    /**
     * Sends a message to the actor associated with this `LocalRef`.
     *
     * @param msg the message to be sent to the actor.
     * @return a `Result` indicating success or failure of the operation.
     */
    override suspend fun tell(msg: ActorProtocol): Result<Unit> =
        actor().getOrElse { return Result.failure(it) }.tell(msg)

    /**
     * Sends a message to the associated actor and waits for a response within a specified timeout duration.
     * The method follows the "ask" pattern where a request message is sent, and the sender awaits a corresponding response.
     *
     * @param msg The message to be sent, which must extend [ActorProtocol.Message] and have a corresponding response type [R].
     * @param timeout The maximum duration to wait for a response before timing out.
     * @return A [Result] containing the response of type [R] if successful. Returns a failure if the operation encounters an error or times out.
     */
    override suspend fun <R, M> ask(msg: M, timeout: Duration): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response =
        actor().getOrElse { return Result.failure(it) }.ask(msg, timeout)

    /**
     * Retrieves the current status of the actor associated with this `LocalRef`.
     *
     * @return A `Result` containing the `Actor.Status` if successful, or an error if the operation fails.
     */
    override suspend fun status(): Result<Actor.Status> {
        val status = actor().getOrElse { return Result.failure(it) }.status()
        return Result.success(status)
    }

    /**
     * Retrieves the statistical information of the actor associated with this `LocalRef`.
     *
     * @return A `Result` containing the `Actor.Stats` if successful, or an error in case of failure.
     */
    override suspend fun stats(): Result<Actor.Stats> {
        val stats = actor().getOrElse { return Result.failure(it) }.stats()
        return Result.success(stats)
    }

    /**
     * Initiates the shutdown process for the actor associated with this `LocalRef`.
     *
     * This method triggers the shutdown mechanism in the associated actor, ensuring
     * a graceful release of resources and a transition into the shutting down state.
     *
     * @return A `Result` indicating the completion of the shutdown operation. Returns `Result.success(Unit)` if successful,
     * or `Result.failure` with an error if the operation fails.
     */
    override suspend fun shutdown(): Result<Unit> {
        actor().getOrElse { return Result.failure(it) }.shutdown()
        return Result.success(Unit)
    }

    /**
     * Invalidates the current actor reference by setting it to `null`.
     *
     * This method is typically used to clear or reset the state of the `LocalRef` by
     * disposing of its reference to the associated actor. After calling this method,
     * the `LocalRef` will no longer be linked to the actor until re-associated.
     */
    internal fun invalidate() {
        actor = null
    }

    /**
     * Retrieves the actor associated with this `LocalRef`.
     * If the actor is not already initialized, it is fetched from the `ActorSystem` registry.
     *
     * @return The actor instance associated with this `LocalRef`.
     */
    private suspend fun actor(): Result<AnyActor> =
        runCatching { actor ?: ActorSystem.registry.getLocalActor(this) }

    /**
     * Provides the string representation of the `LocalRef` instance.
     *
     * @return A string in the format "LocalRef(address)", where `address` is the unique address of the actor.
     */
    override fun toString(): String = "LocalRef($address)"
}
