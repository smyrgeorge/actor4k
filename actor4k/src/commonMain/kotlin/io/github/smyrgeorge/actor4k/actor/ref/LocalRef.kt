package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlin.reflect.KClass
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

    private var actor: AnyActor?
    val clazz: KClass<out AnyActor>

    /**
     * Creates a new `LocalRef` instance.
     *
     * @param address The unique `Address` of the actor in the actor system.
     * @param actor The actor instance to be referenced.
     */
    internal constructor(address: Address, actor: AnyActor) : super(address) {
        this.actor = actor
        this.clazz = actor::class
    }

    /**
     * Sends a message to the actor associated with this `LocalRef`.
     *
     * @param msg the message to be sent
     * @return Unit
     */
    override suspend fun tell(msg: Actor.Message): Unit = actor().tell(msg)

    /**
     * Sends a message to the associated actor and awaits a response within the specified timeout period.
     *
     * @param Res the type of the expected response.
     * @param msg the message to be sent to the actor.
     * @param timeout the maximum duration to wait for a response.
     * @return the response received from the actor.
     */
    override suspend fun <Res> ask(msg: Actor.Message, timeout: Duration): Res {
        @Suppress("UNCHECKED_CAST")
        return actor().ask(msg, timeout) as Res
    }

    /**
     * Retrieves the current status of the actor associated with this `LocalRef`.
     *
     * @return the current status of the actor.
     */
    override suspend fun status(): Actor.Status = actor().status()

    /**
     * Retrieves statistical information about the actor associated with this `LocalRef`.
     *
     * @return An instance of `Actor.Stats` containing statistics related to the actor.
     */
    override suspend fun stats(): Actor.Stats = actor().stats()

    /**
     * Shuts down the actor associated with this `LocalRef`.
     *
     * This method retrieves the actor instance from the registry and initiates
     * its shutdown process. Once invoked, the actor transitions to a shutting down
     * state and ceases processing messages. The actor's resources, such as its mailbox,
     * are released during this process.
     */
    override suspend fun shutdown() = actor().shutdown()

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
    private suspend fun actor(): AnyActor = actor ?: ActorSystem.registry.getLocalActor(this)

    override fun toString(): String = "LocalRef($address)"
}
