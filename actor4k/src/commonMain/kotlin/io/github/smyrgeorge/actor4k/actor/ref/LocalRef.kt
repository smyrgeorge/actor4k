package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlin.reflect.KClass

/**
 * A reference to a local actor in the actor system. This class extends `ActorRef` and provides
 * functionality to interact with an actor hosted locally within the system.
 *
 * @property address The unique address identifying the actor.
 * @property actor The `KClass` of the actor associated with this reference.
 */
data class LocalRef(
    override val address: Address,
    val actor: KClass<out Actor>,
) : ActorRef(address) {
    /**
     * Sends a message to the actor associated with this `LocalRef`.
     *
     * @param msg the message to be sent
     * @return Unit
     */
    override suspend fun tell(msg: Any): Unit =
        ActorSystem.registry.get(this).tell(msg)

    /**
     * Sends a message to the actor associated with this `LocalRef` and waits for a response.
     *
     * @param msg The message to be sent to the actor.
     * @return The response received from the actor.
     */
    override suspend fun <R> ask(msg: Any): R =
        ActorSystem.registry.get(this).ask(msg)

    /**
     * Retrieves the current status of the actor associated with this `LocalRef`.
     *
     * @return the current status of the actor.
     */
    suspend fun status(): Actor.Status =
        ActorSystem.registry.get(this).status()

    /**
     * Shuts down the actor associated with this `LocalRef`.
     *
     * This method retrieves the actor instance from the registry and initiates
     * its shutdown process. Once invoked, the actor transitions to a shutting down
     * state and ceases processing messages. The actor's resources, such as its mailbox,
     * are released during this process.
     */
    suspend fun shutdown() =
        ActorSystem.registry.get(this).shutdown()
}
