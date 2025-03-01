package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Abstract class responsible for managing the registry of `Actor` instances.
 * This class provides mechanisms for storing, retrieving, unregistering, and shutting down actors,
 * ensuring thread-safety and consistency through a mutex lock.
 */
abstract class ActorRegistry {

    val log: Logger = try {
        ActorSystem.loggerFactory.getLogger(this::class)
    } catch (_: UninitializedPropertyAccessException) {
        error("Please register first a Logger.Factory to the ActorSystem.")
    }

    // Mutex for the create operation.
    val mutex = Mutex()

    /**
     * Represents a registry for storing locally managed `Actor` instances.
     *
     * This mutable map is used to maintain the association between unique string keys
     * and their corresponding `Actor` instances. The `local` map allows efficient
     * retrieval, registration, and management of actors that are instantiated and
     * maintained within the current context.
     *
     * The key is a unique identifier for each actor, and the value is the `Actor`
     * instance associated with that key. It is primarily used internally for operations
     * such as actor registration, retrieval, and shutdown.
     *
     * Operations on this map should be synchronized or externally controlled to ensure
     * thread safety, as it may be accessed by multiple coroutines or concurrent processes.
     */
    val local: MutableMap<String, Actor> = mutableMapOf()

    init {
        launch {
            while (true) {
                runCatching {
                    delay(ActorSystem.conf.registryCleanup)
                    stopLocalExpired()
                }
            }
        }
    }

    /**
     * Retrieves an instance of an `Actor` based on the provided `LocalRef`.
     * If the actor is not found in the local registry, it attempts to retrieve it using
     * its actor type and key, and updates the local registry with the result.
     *
     * @param ref The `LocalRef` representing the actor to be retrieved.
     * @return The `Actor` instance associated with the given `LocalRef`.
     */
    suspend fun get(ref: LocalRef): Actor =
        local[ref.address] ?: get(ref.actor, ref.key).let { local[ref.address]!! }

    /**
     * Retrieves an `ActorRef` for the specified actor type and key.
     *
     * @param actor The class of the actor to be retrieved.
     * @param key A unique string key associated with the actor.
     * @return An `ActorRef` corresponding to the requested actor type and key.
     */
    abstract suspend fun <A : Actor> get(actor: KClass<A>, key: String): ActorRef

    /**
     * Unregisters an actor from the registry using its type and key.
     *
     * @param actor The actor instance to be unregistered.
     * @return Unit A coroutine completion indicating that the operation has finished.
     */
    suspend fun unregister(actor: Actor): Unit = unregister(actor::class, actor.key)

    /**
     * Unregisters an actor of the specified type and key from the local registry.
     * If the `force` parameter is set to false, the actor cannot be unregistered
     * unless its status is `FINISHED`.
     *
     * @param actor The class of the actor to be unregistered.
     * @param key A unique string key associated with the actor.
     * @param force Whether to forcibly unregister the actor, even if its status is not `FINISHED`.
     */
    suspend fun <A : Actor> unregister(actor: KClass<A>, key: String, force: Boolean = false) {
        val address = Actor.addressOf(actor, key)
        withLock {
            local[address]?.let {
                if (!force && it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                local.remove(address)
                log.info("Unregistered actor $address.")
            }
        }
    }

    /**
     * Initiates the shutdown process for all locally registered actors.
     *
     * This method ensures that all actors in the local registry are stopped by invoking their respective
     * shutdown functions. The operation is synchronized to prevent concurrent modifications to the local
     * registry during the shutdown process.
     *
     * @return Unit A coroutine completion indicating that all local actors have been successfully shut down.
     */
    suspend fun shutdown(): Unit = withLock {
        log.debug("Stopping all local actors.")
        local.values.forEach { it.shutdown() }
    }

    /**
     * Counts the number of actors currently stored in the local registry.
     *
     * @return The total number of actors in the local registry.
     */
    fun count(): Int = local.size

    /**
     * Calculates the total number of messages processed by all actors in the local registry.
     *
     * @return The total count of messages processed by all actors.
     */
    fun totalMessages(): Long = local.map { it.value.stats().messages }.sum()

    private suspend fun stopLocalExpired(): Unit = withLock {
        log.debug("Stopping all local expired actors.")
        local.values.forEach {
            val df = Instant.now().epochSecond - it.stats().last.epochSecond
            if (df > ActorSystem.conf.actorExpiration.inWholeSeconds) {
                log.info("Closing ${it.address()}, ${it.stats()} (expired).")
                it.shutdown()
            }
        }
    }

    /**
     * Acquires a lock before executing the provided suspend function.
     *
     * @param f The suspend function to be executed within the lock.
     * @return Unit
     */
    suspend fun <T> withLock(f: suspend () -> T): T = mutex.withLock { f() }
}
