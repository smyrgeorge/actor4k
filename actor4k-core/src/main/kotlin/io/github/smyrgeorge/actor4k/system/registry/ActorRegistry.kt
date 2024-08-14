package io.github.smyrgeorge.actor4k.system.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
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
abstract class ActorRegistry {

    val log = KotlinLogging.logger {}

    // Mutex for the create operation.
    val mutex = Mutex()

    // Stores [Actor].
    val local: MutableMap<String, Actor> = mutableMapOf()

    init {
        launchGlobal {
            while (true) {
                delay(ActorSystem.conf.registryCleanup)
                stopLocalExpired()
            }
        }
    }

    suspend fun <A : Actor> get(actor: KClass<A>, key: String, shard: String = key): ActorRef =
        get(actor.java, key, shard)

    abstract suspend fun <A : Actor> get(actor: Class<A>, key: String, shard: String = key): ActorRef

    suspend fun get(clazz: String, key: String, shard: String): ActorRef {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")

        val address = Actor.addressOf(actor, key)
        return local[address]?.ref() ?: get(actor, key, shard)
    }

    suspend fun get(ref: LocalRef): Actor =
        local[ref.address] ?: get(ref.actor, ref.key).let { local[ref.address]!! }

    suspend fun unregister(actor: Actor): Unit =
        unregister(actor::class.java, actor.key)

    abstract suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean = false)

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

    fun count(): Int = local.count()
    fun asJava(): JActorRegistry = JActorRegistry
}
