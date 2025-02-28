package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.callSuspend
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.InvocationTargetException

class SimpleActorRegistry : ActorRegistry() {
    override suspend fun <A : Actor> get(actor: Class<A>, key: String, shard: String): ActorRef {
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
                .getConstructor(String::class.java, String::class.java)
                .newInstance(shard, key)

            // Store [Actor.Ref] to the local storage.
            local[address] = a

            a.ref() to a
        }

        // Invoke activate (initialization) method.
        @Suppress("DuplicatedCode")
        try {
            actor.callSuspend(actorInstance, "activate")
        } catch (e: InvocationTargetException) {
            log.error("Could not activate ${actorInstance.address()}. Reason: ${e.targetException.message}")
            unregister(actor = actor, key = key, force = true)
            throw e
        } catch (e: Exception) {
            log.error("Could not activate ${actorInstance.address()}.")
            unregister(actor = actor, key = key, force = true)
            throw e
        }

        log.debug("Actor $address created and activated successfully.")
        return ref
    }

    override suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean) {
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
