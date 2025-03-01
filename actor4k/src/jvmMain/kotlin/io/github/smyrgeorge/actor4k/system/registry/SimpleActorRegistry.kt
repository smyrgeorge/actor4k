package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.primaryConstructor

class SimpleActorRegistry : ActorRegistry() {
    override suspend fun <A : Actor> get(actor: KClass<A>, key: String): ActorRef {
        // Calculate the actor address.
        val address: String = Actor.addressOf(actor, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create $address, system is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val actorInstance: Actor = mutex.withLock {
            // Check if the actor already exists in the local storage.
            local[address]?.let { return it.ref() }

            // Spawn the actor.
            val a: Actor = actor
                .primaryConstructor!!
                .callSuspend(key)

            // Store [Actor.Ref] to the local storage.
            local[address] = a

            a
        }

        // Invoke activate (initialization) method.
        try {
            actorInstance.activate()
        } catch (e: Exception) {
            log.error("Could not activate ${actorInstance.address()}.")
            unregister(actor = actor, key = key, force = true)
            throw e
        }

        log.debug("Actor $address created and activated successfully.")
        return actorInstance.ref()
    }
}
