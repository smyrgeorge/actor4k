package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.util.callSuspend
import io.github.smyrgeorge.actor4k.util.chunked
import io.github.smyrgeorge.actor4k.util.forEachParallel
import io.github.smyrgeorge.actor4k.util.java.JActorRegistry
import io.github.smyrgeorge.actor4k.util.launchGlobal
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate")
class ActorRegistry {

    private val log = KotlinLogging.logger {}

    // Mutex for the create operation.
    private val mutex = Mutex()

    // Stores [Actor].
    private val local: MutableMap<String, Actor> = mutableMapOf()

    // Stores [Actor.Ref.Remote].
    private val remote: MutableMap<String, Actor.Ref.Remote> = mutableMapOf()

    init {
        launchGlobal {
            while (true) {
                delay(ActorSystem.conf.registryCleanup)
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
        // Calculate the actor address.
        val address: String = Actor.addressOf(actor, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create $address, cluster is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val (ref: Actor.Ref, actorInstance: Actor?) = mutex.withLock {
            // Check if the actor already exists in the local storage.
            local[address]?.let { return it.ref() }

            // Create Local/Remote actor.
            if (ActorSystem.isCluster()
                && ActorSystem.cluster.nodeOf(shard).dc != ActorSystem.cluster.conf.alias
            ) {
                val existing = remote[address]
                if (existing != null && Instant.now().isBefore(existing.exp)) return existing
                else remote.remove(address)

                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.GetActor(shard, actor.name, key)
                val ref = ActorSystem.cluster
                    .msg(msg)
                    .getOrThrow<Envelope.GetActor.Ref>()
                    .toRef(shard)
                    .also { remote[address] = it }

                ref to null
            } else {
                // Case Local.
                // Spawn the actor.
                val a: Actor = actor
                    .getConstructor(String::class.java, String::class.java)
                    .newInstance(shard, key)

                // Store [Actor.Ref] to the local storage.
                local[address] = a

                // Declare shard to the [ShardManager]
                ShardManager.operation(ShardManager.Op.REGISTER, shard)

                a.ref() to a
            }
        }

        // Invoke activate (initialization) method.
        actorInstance?.let {
            try {
                actor.callSuspend(it, "activate")
            } catch (e: InvocationTargetException) {
                log.error { "Could not activate ${it.address()}. Reason: ${e.targetException.message}" }
                unregister(actor = actor, key = key, force = true)
                throw e
            } catch (e: Exception) {
                log.error { "Could not activate ${it.address()}." }
                unregister(actor = actor, key = key, force = true)
                throw e
            }
        }

        log.debug { "Actor $address created and activated successfully." }
        return ref
    }

    suspend fun get(clazz: String, key: String, shard: String): Actor.Ref {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")

        val address = Actor.addressOf(actor, key)
        return local[address]?.ref() ?: get(actor, key, shard)
    }

    suspend fun get(ref: Actor.Ref.Local): Actor =
        local[ref.address] ?: get(ref.actor, ref.key).let { local[ref.address]!! }

    suspend fun unregister(actor: Actor): Unit =
        unregister(actor::class.java, actor.key)

    suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean = false) {
        val address = Actor.addressOf(actor, key)
        mutex.withLock {
            local[address]?.let {
                if (!force && it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                local.remove(address)
                ShardManager.operation(ShardManager.Op.UNREGISTER, it.shard)
                log.info { "Unregistered actor $address." }
            }
        }
    }

    suspend fun stopAll(): Unit = mutex.withLock {
        log.debug { "Stopping all local actors." }
        local.values.chunked(local.size, 4).forEachParallel { l ->
            l.forEach { it.shutdown() }
        }
    }

    private suspend fun stopLocalExpired(): Unit = mutex.withLock {
        log.debug { "Stopping all local expired actors." }
        local.values.chunked(local.size, 4).forEachParallel { l ->
            l.forEach {
                val df = Instant.now().epochSecond - it.stats().last.epochSecond
                if (df > ActorSystem.conf.actorExpiration.inWholeSeconds) {
                    log.info { "Closing ${it.address()}, ${it.stats()} (expired)." }
                    it.shutdown()
                }
            }
        }
    }

    private suspend fun removeRemoteExpired(): Unit = mutex.withLock {
        log.debug { "Removing all remote expired actors." }
        remote.values.chunked(remote.size, 4).forEachParallel { l ->
            l.forEach { if (Instant.now().isAfter(it.exp)) remote.remove(it.address) }
        }
    }

    fun count(): Int = local.count()
    fun asJava(): JActorRegistry = JActorRegistry
}
