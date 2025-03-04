package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlin.reflect.KClass

class SimpleActorRegistry : ActorRegistry() {
    override suspend fun <A : Actor> get(clazz: KClass<A>, key: String): ActorRef {
        // Calculate the actor address.
        val address: Address = Address.of(clazz, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Failed to get $address, ActorSystem is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val (isNew: Boolean, actor: Actor) = lock {
            // Check if the actor already exists in the local storage.
            registry[address]?.let { return@lock false to it }

            // Spawn the actor.
            val a: Actor = factory(clazz)(key)

            // Store the [Actor] to the local storage.
            registry[address] = a

            true to a
        }

        // Only call the activate method if the Actor just created.
        if (isNew) {
            try {
                // Invoke activate (initialization) method of the Actor.
                actor.activate()
                log.debug("Actor {} activated successfully.", address)
            } catch (e: Exception) {
                log.error("Could not activate ${actor.address()}. Reason: ${e.message ?: "Unknown error."}.")
                registry.remove(address)
                throw e
            }
        }

        return actor.ref()
    }
}
