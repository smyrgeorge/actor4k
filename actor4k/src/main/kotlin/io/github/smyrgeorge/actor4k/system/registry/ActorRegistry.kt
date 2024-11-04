package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.java.JActorRegistry
import io.github.smyrgeorge.actor4k.util.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.reflect.KClass

/**
 * The `ActorRegistry` class is an abstract class that manages the registry of actors within the system.
 * It provides functionalities to register, unregister, retrieve, and manage the lifecycle of actors.
 * Additionally, it handles the cleanup of expired actors periodically.
 *
 * This class uses coroutine-based concurrency mechanisms (e.g., Mutex, coroutine locks) to ensure thread-safe operations.
 *
 * Properties:
 * - `log`: Logger instance for logging.
 * - `mutex`: Mutex for synchronization during the creation of actors.
 * - `local`: Mutable map to store local actors keyed by their addresses.
 *
 * Constructor:
 * - Initializes a coroutine that periodically cleans up expired actors based on the configuration.
 *
 * Methods:
 *
 * - `suspend fun get(ref: LocalRef): Actor`:
 *   Retrieves an actor from the local registry or creates and registers a new one if it does not exist.
 *
 * - `suspend fun <A : Actor> get(actor: KClass<A>, key: String, shard: String = key): ActorRef`:
 *   Retrieves an actor by its class type, key, and optional shard, returning an `ActorRef`.
 *
 * - `abstract suspend fun <A : Actor> get(actor: Class<A>, key: String, shard: String = key): ActorRef`:
 *   Abstract method to be implemented by subclasses for retrieving an actor reference.
 *
 * - `suspend fun unregister(actor: Actor): Unit`:
 *   Unregisters an actor from the registry by its class and key.
 *
 * - `abstract suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean = false)`:
 *   Abstract method to be implemented by subclasses to unregister an actor.
 *
 * - `suspend fun stopAll(): Unit`:
 *   Stops all local actors by shutting them down concurrently.
 *
 * - `private suspend fun stopLocalExpired(): Unit`:
 *   Stops all expired local actors based on their last activity time.
 *
 * - `fun count(): Int`:
 *   Returns the current count of actors in the local registry.
 *
 * - `fun totalMessages(): Long`:
 *   Returns the total number of messages processed by all actors in the local registry.
 *
 * - `fun asJava(): JActorRegistry`:
 *   Converts the current registry to a Java-compatible `JActorRegistry` instance.
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class ActorRegistry {

    val log: Logger = LoggerFactory.getLogger(this::class.java)

    // Mutex for the create operation.
    val mutex = Mutex()

    // Stores [Actor].
    val local: MutableMap<String, Actor> = mutableMapOf()

    init {
        launch {
            while (true) {
                delay(ActorSystem.conf.registryCleanup)
                stopLocalExpired()
            }
        }
    }

    suspend fun get(ref: LocalRef): Actor =
        local[ref.address] ?: get(ref.actor, ref.key).let { local[ref.address]!! }

    suspend fun <A : Actor> get(
        actor: KClass<A>,
        key: String,
        shard: String = key
    ): ActorRef = get(actor.java, key, shard)

    abstract suspend fun <A : Actor> get(
        actor: Class<A>,
        key: String,
        shard: String = key
    ): ActorRef

    suspend fun unregister(actor: Actor): Unit = unregister(actor::class.java, actor.key)
    abstract suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean = false)

    suspend fun stopAll(): Unit = mutex.withLock {
        log.debug("Stopping all local actors.")
        local.values.forEach { it.shutdown() }
    }

    private suspend fun stopLocalExpired(): Unit = mutex.withLock {
        log.debug("Stopping all local expired actors.")
        local.values.forEach {
            val df = Instant.now().epochSecond - it.stats().last.epochSecond
            if (df > ActorSystem.conf.actorExpiration.inWholeSeconds) {
                log.info("Closing ${it.address()}, ${it.stats()} (expired).")
                it.shutdown()
            }
        }
    }

    fun count(): Int = local.size
    fun totalMessages(): Long = local.map { it.value.stats().messages }.sum()
    fun asJava(): JActorRegistry = JActorRegistry
}
