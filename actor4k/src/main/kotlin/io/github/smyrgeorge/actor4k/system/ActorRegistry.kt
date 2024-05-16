package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.util.chunked
import io.github.smyrgeorge.actor4k.util.forEachParallel
import io.github.smyrgeorge.actor4k.util.java.JActorRegistry
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object ActorRegistry {

    private val log = KotlinLogging.logger {}

    // Stores [Actor].
    private val local = ConcurrentHashMap<String, Actor>(/* initialCapacity = */ 1024)

    // Stores [Actor.Ref.Remote].
    private val remote = ConcurrentHashMap<String, Actor.Ref.Remote>(/* initialCapacity = */ 1024)

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ActorSystem.Conf.registryCleanup)
                stopLocalExpired()
                removeRemoteExpired()
            }
        }
    }

    suspend fun <A : Actor> get(
        actor: KClass<A>,
        key: String,
        shard: String = key
    ): Actor.Ref = get(actor.java, key, shard)

    suspend fun <A : Actor> get(
        actor: Class<A>,
        key: String,
        shard: String = key
    ): Actor.Ref {
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create actor because cluster is ${ActorSystem.status}.")

        val address = Actor.addressOf(actor, key)

        // Check if the actor already exists in the local storage.
        local[address]?.let { return it.ref() }

        // Create Local/Remote actor.
        val ref: Actor.Ref =
            if (ActorSystem.clusterMode
                && ActorSystem.cluster.nodeOf(shard).dc != ActorSystem.cluster.conf.alias
            ) {
                val existing = remote[address]
                if (existing != null && Instant.now().isBefore(existing.exp)) return existing
                else remote.remove(address)

                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.GetActor(shard, actor.name, key)
                ActorSystem.cluster.msg(msg).getOrThrow<Envelope.GetActor.Ref>().toRef(shard).also {
                    remote[address] = it
                }
            } else {
                // Case Local.
                // Spawn the actor.
                val a: Actor = actor
                    .getConstructor(String::class.java, String::class.java)
                    .newInstance(shard, key)

                // Invoke activate (initialization) method.
                actor.getMethod("activate").invoke(a)

                // Store [Actor.Ref] to the local storage.
                local[address] = a

                // Declare shard to the [ShardManager]
                ShardManager.operation(ShardManager.Op.REGISTER, shard)

                a.ref()
            }

        return ref
    }

    suspend fun get(clazz: String, key: String, shard: String): Actor.Ref {
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

        return local[ref.address] // If the actor is not in the registry (passivated) spawn a new one.
            ?: get(ref.actor, ref.key).let { local[ref.address]!! }
    }

    fun <A : Actor> unregister(actor: Class<A>, key: String) {
        val address = Actor.addressOf(actor, key)
        local[address]?.let {
            if (it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
            local.remove(address)
            ShardManager.operation(ShardManager.Op.UNREGISTER, it.shard)
        }
    }

    suspend fun stopAll(): Unit =
        local.values
            .chunked(local.size, 4)
            .forEachParallel { l -> l.forEach { it.stop() } }

    private suspend fun stopLocalExpired(): Unit =
        local.values
            .chunked(local.size, 4)
            .forEachParallel { l ->
                l.forEach {
                    val df = Instant.now().epochSecond - it.stats().last.epochSecond
                    if (df > ActorSystem.Conf.actorExpiration.inWholeSeconds) {
                        log.debug { "Closing ${it.address()} (expired)." }
                        it.stop()
                    }
                }
            }

    private suspend fun removeRemoteExpired(): Unit =
        remote.values
            .chunked(remote.size, 4)
            .forEachParallel { l -> l.forEach { if (Instant.now().isAfter(it.exp)) remote.remove(it.address) } }

    fun count(): Int = local.count()

    fun asJava(): JActorRegistry = JActorRegistry
}
