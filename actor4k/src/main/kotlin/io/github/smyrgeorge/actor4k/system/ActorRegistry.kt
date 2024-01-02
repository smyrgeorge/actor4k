package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.shard.Shard
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.util.forEachParallel
import kotlinx.coroutines.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object ActorRegistry {

    private val log = KotlinLogging.logger {}

    // Only stores [Actor.Ref.Local].
    private val registry = ConcurrentHashMap<String, Actor>(/* initialCapacity = */ 1024)

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ActorSystem.Conf.registryCleanup.toMillis())
                stopExpired()
            }
        }
    }

    suspend fun <A : Actor> get(
        actor: KClass<A>,
        key: Actor.Key,
        shard: Shard.Key = Shard.Key(key.value)
    ): Actor.Ref = get(actor.java, key, shard)

    suspend fun <A : Actor> get(
        actor: Class<A>,
        key: Actor.Key,
        shard: Shard.Key = Shard.Key(key.value)
    ): Actor.Ref {
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create actor because cluster is ${ActorSystem.status}.")

        val address = Actor.addressOf(actor, key)

        // Check if the actor already exists in the local storage.
        registry[address]?.let { return it.ref() }

        // Create Local/Remote actor.
        val ref: Actor.Ref =
            if (ActorSystem.clusterMode
                && ActorSystem.cluster.nodeOf(shard).dc != ActorSystem.cluster.node.alias
            ) {
                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.GetActor(shard, actor.name, key)
                ActorSystem.cluster.msg(msg).getOrThrow<Envelope.GetActor.Ref>().toRef(shard)
            } else {
                // Case Local.
                // Spawn the actor.
                val a: Actor = actor
                    .getConstructor(Shard.Key::class.java, Actor.Key::class.java)
                    .newInstance(shard, key)

                // Store [Actor.Ref] to the local storage.
                registry[address] = a
                a.ref()
            }

        return ref
    }

    suspend fun get(clazz: String, key: Actor.Key, shard: Shard.Key): Actor.Ref {
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create actor because cluster is ${ActorSystem.status}.")

        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")
        return get(actor, key, shard)
    }

    suspend fun get(ref: Actor.Ref.Local): Actor {
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create actor because cluster is ${ActorSystem.status}.")

        return registry[ref.address] // If the actor is not in the registry (passivated) spawn a new one.
            ?: get(ref.actor, ref.key).let { registry[ref.address]!! }
    }

    fun <A : Actor> unregister(actor: Class<A>, key: Actor.Key) {
        val address = Actor.addressOf(actor, key)
        registry[address]?.let {
            if (it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
            registry.remove(address)
        }
    }

    suspend fun stopAll(): Unit =
        registry.values.forEachParallel { it.stop() }

    private suspend fun stopExpired(): Unit =
        registry.values.forEachParallel {
            val df = Instant.now().epochSecond - it.stats().last.epochSecond
            if (df > ActorSystem.Conf.actorExpiration.seconds) {
                log.debug { "Closing ${it.address()} (expired)." }
                it.stop()
            }
        }

    fun count(): Int = registry.count()
}