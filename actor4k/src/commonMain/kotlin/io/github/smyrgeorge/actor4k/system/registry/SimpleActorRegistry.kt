package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlin.reflect.KClass

class SimpleActorRegistry : ActorRegistry() {
    override suspend fun <A : Actor> get(actor: KClass<A>, key: String): ActorRef {
        // Calculate the actor address.
        val address: Address = Actor.addressOf(actor, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create $address, system is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val actorInstance: Actor = lock {
            // Check if the actor already exists in the local storage.
            registry[address]?.let { return@lock it }

            // Spawn the actor.
            val a: Actor = factory(actor)(key)

            // Store the [Actor] to the local storage.
            registry[address] = a

            a
        }

        try {
            // Invoke activate (initialization) method of the Actor.
            actorInstance.activate()
        } catch (e: Exception) {
            log.error("Could not activate ${actorInstance.address()}.")
            unregister(actor = actor, key = key, force = true)
            throw e
        }

        log.debug("Actor {} created and activated successfully.", address)
        return actorInstance.ref()
    }
}
