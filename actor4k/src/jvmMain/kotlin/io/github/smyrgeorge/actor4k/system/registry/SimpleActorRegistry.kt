package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.primaryConstructor

class SimpleActorRegistry : ActorRegistry() {
    override suspend fun <A : Actor> get(actor: KClass<A>, key: String, shard: String): ActorRef {
        // Calculate the actor address.
        val address: String = Actor.addressOf(actor, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create $address, system is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val (ref: ActorRef, actorInstance: Actor) = mutex.withLock {
            // Check if the actor already exists in the local storage.
            local[address]?.let { return it.ref() }

            // Spawn the actor.
            val a: Actor = actor
                .primaryConstructor!!
                .callSuspend(shard, key)

            // Store [Actor.Ref] to the local storage.
            local[address] = a

            a.ref() to a
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
        return ref
    }

    override suspend fun <A : Actor> unregister(actor: KClass<A>, key: String, force: Boolean) {
        val address = Actor.addressOf(actor, key)
        mutex.withLock {
            local[address]?.let {
                if (!force && it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                local.remove(address)
                log.info("Unregistered actor $address.")
            }
        }
    }
}
