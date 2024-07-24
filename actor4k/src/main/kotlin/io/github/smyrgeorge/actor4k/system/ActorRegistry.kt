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
import java.time.Instant
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate")
object ActorRegistry {

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
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create actor because cluster is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val (ref: Actor.Ref, actorInstance: Actor?) = mutex.withLock {

            // Calculate the actor address.
            val address: String = Actor.addressOf(actor, key)

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
        actorInstance?.let { actor.callSuspend("activate", it) }

        log.debug { "Actor $ref created." }
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

    suspend fun deregister(actor: Actor): Unit =
        deregister(actor::class.java, actor.key)

    suspend fun <A : Actor> deregister(actor: Class<A>, key: String) {
        val address = Actor.addressOf(actor, key)
        mutex.withLock {
            local[address]?.let {
                if (it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                local.remove(address)
                ShardManager.operation(ShardManager.Op.UNREGISTER, it.shard)
                log.debug { "Unregistered actor $address." }
            }
        }
    }

    suspend fun stopAll(): Unit = mutex.withLock {
        log.debug { "Stopping all local actors. Total: ${local.size}" }
        local.values.chunked(local.size, 4).forEachParallel { l ->
            l.forEach { it.shutdown() }
        }
    }

    private suspend fun stopLocalExpired(): Unit = mutex.withLock {
        log.debug { "Stopping all local expired actors. Total: ${local.size}" }
        local.values.chunked(local.size, 4).forEachParallel { l ->
            l.forEach {
                val df = Instant.now().epochSecond - it.stats().last.epochSecond
                if (df > ActorSystem.conf.actorExpiration.inWholeSeconds) {
                    log.debug { "Closing ${it.address()} (expired)." }
                    it.shutdown()
                }
            }
        }
    }

    private suspend fun removeRemoteExpired(): Unit = mutex.withLock {
        log.debug { "Removing all remote expired actors. Total: ${remote.size}" }
        remote.values.chunked(remote.size, 4).forEachParallel { l ->
            l.forEach { if (Instant.now().isAfter(it.exp)) remote.remove(it.address) }
        }
    }

    fun count(): Int = local.count()
    fun asJava(): JActorRegistry = JActorRegistry
}
