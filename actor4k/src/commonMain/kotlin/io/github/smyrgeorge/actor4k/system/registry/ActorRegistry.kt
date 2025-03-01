package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.ActorFactory
import io.github.smyrgeorge.actor4k.util.extentions.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.reflect.KClass

/**
 * Abstract class responsible for managing the registry of `Actor` instances.
 * This class provides mechanisms for storing, retrieving, unregistering, and shutting down actors,
 * ensuring thread-safety and consistency through a mutex lock.
 */
abstract class ActorRegistry {

    val log: Logger by lazy {
        try {
            ActorSystem.loggerFactory.getLogger(this::class)
        } catch (_: Exception) {
            error("Please register first a Logger.Factory to the ActorSystem.")
        }
    }

    // Mutex for the create operation.
    private val mutex = Mutex()

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
    val registry: MutableMap<Address, Actor> = mutableMapOf()

    /**
     * A registry of factory functions used to create instances of different `Actor` types.
     * Each factory function is associated with a unique string identifier corresponding to an actor type.
     *
     * This map serves as the internal storage for registering and retrieving actor factory functions.
     * Factory functions are registered via the `register` method and retrieved through the `factory` method.
     * It is used to manage the creation of actor instances dynamically within the `ActorRegistry`.
     */
    private val factories: MutableMap<String, (key: String) -> Actor> = mutableMapOf()

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
        registry[ref.address] ?: get(ref.actor, ref.address.key).let { registry[ref.address]!! }

    /**
     * Retrieves an `ActorRef` for the specified actor type and key.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param key A unique string key associated with the actor.
     * @return An `ActorRef` corresponding to the requested actor type and key.
     */
    abstract suspend fun <A : Actor> get(clazz: KClass<A>, key: String): ActorRef

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
        val address = Address.of(actor, key)
        lock {
            registry[address]?.let {
                if (!force && it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                registry.remove(address)
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
    suspend fun shutdown(): Unit = lock {
        log.debug("Stopping all local actors.")
        registry.values.forEach { it.shutdown() }
    }

    /**
     * Counts the number of actors currently stored in the local registry.
     *
     * @return The total number of actors in the local registry.
     */
    fun size(): Int = registry.size

    /**
     * Calculates the total number of messages processed by all actors in the local registry.
     *
     * @return The total count of messages processed by all actors.
     */
    fun totalMessages(): Long = registry.map { it.value.stats().messages }.sum()

    /**
     * Registers a factory function for creating instances of a specific actor type within the ActorRegistry.
     *
     * @param actor The class of the actor to be registered.
     * @param factory A lambda function that takes a string key as a parameter and returns an instance of the actor.
     * @return The updated ActorRegistry instance.
     */
    fun register(actor: KClass<out Actor>, factory: ActorFactory): ActorRegistry {
        if (ActorSystem.status == ActorSystem.Status.READY) error("Cannot register a factory while the system is ready.")
        this.factories[actor.qualifiedName!!] = factory
        return this
    }

    /**
     * Retrieves a factory function for creating instances of a specific actor type.
     * The factory function takes a string key as a parameter and returns an instance of the actor.
     *
     * @param actor The class of the actor for which the factory function is requested.
     * @return A lambda function that takes a string key and returns an instance of the specified actor type.
     * @throws IllegalStateException if no factory is registered for the provided actor type.
     */
    fun factory(actor: KClass<out Actor>): ActorFactory =
        factories[actor.qualifiedName!!] ?: error("No factory registered for ${actor.qualifiedName!!}.")

    /**
     * Acquires a lock before executing the provided suspend function.
     *
     * @param f The suspend function to be executed within the lock.
     * @return Unit
     */
    suspend fun <T> lock(f: suspend () -> T): T = mutex.withLock { f() }

    /**
     * Stops and removes all locally registered actors that have exceeded their expiration time.
     *
     * This method iterates through all actors in the local registry, calculates their
     * inactivity duration since the last recorded activity, and shuts down actors whose
     * durations exceed the configured expiration threshold. The operation is performed
     * within a lock to ensure thread safety and prevent concurrent modifications to the
     * registry.
     *
     * @return Unit A coroutine completion indicating the operation has finished.
     */
    private suspend fun stopLocalExpired(): Unit = lock {
        log.debug("Stopping all local expired actors.")
        registry.values.forEach {
            val df = (Clock.System.now() - it.stats().last).inWholeSeconds
            if (df > ActorSystem.conf.actorExpiration.inWholeSeconds) {
                log.info("Closing ${it.address()}, ${it.stats()} (expired).")
                it.shutdown()
            }
        }
    }
}
