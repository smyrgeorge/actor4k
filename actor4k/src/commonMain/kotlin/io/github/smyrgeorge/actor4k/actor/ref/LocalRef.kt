package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.Actor.Companion.addressOf
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlin.reflect.KClass

/**
 * `LocalRef` is a data class that represents a reference to a local actor in the `ActorSystem`.
 * It extends `ActorRef` and provides additional functionalities like sending messages,
 * querying status, and stopping the actor.
 *
 * @property name The name of the actor.
 * @property key The key associated with the actor.
 * @property actor The class type of the actor.
 * @property address The address of the actor, defaulting to a computed address using the actor's name and key.
 */
data class LocalRef(
    override val name: String,
    override val key: String,
    val actor: KClass<out Actor>,
    override val address: String = addressOf(name, key)
) : ActorRef(name, key, address) {
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
    suspend fun status(): Actor.Status = ActorSystem.registry.get(this).status()

    /**
     * Stops the actor associated with this `LocalRef`.
     *
     * This function retrieves the actor instance from the registry and initiates
     * the shutdown process, changing the actor's status to `FINISHING` and closing its mailbox.
     *
     * @return Unit
     */
    suspend fun stop() = ActorSystem.registry.get(this).shutdown()
}
