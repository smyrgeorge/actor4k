package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.ActorFactory
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import io.github.smyrgeorge.actor4k.util.extentions.forever
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Represents an abstract registry for managing actor instances in an actor system.
 *
 * The `ActorRegistry` is responsible for the lifecycle management, registration, retrieval,
 * and shutdown of actors. It supports both locally managed actors and dynamic actor creation
 * through factories. The registry ensures thread-safe operations on stored actor instances.
 *
 * @constructor Initializes the `ActorRegistry` with a provided `Logger.Factory` for logging purposes.
 * @param loggerFactory The factory used to obtain a logger instance for logging operations.
 */
abstract class ActorRegistry(loggerFactory: Logger.Factory) {

    val log: Logger = loggerFactory.getLogger(this::class)

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
    private val registry: MutableMap<Address, AnyActor> = mutableMapOf()

    /**
     * A registry of factory functions used to create instances of different `Actor` types.
     * Each factory function is associated with a unique string identifier corresponding to an actor type.
     *
     * This map serves as the internal storage for registering and retrieving actor factory functions.
     * Factory functions are registered via the `register` method and retrieved through the `factory` method.
     * It is used to manage the creation of actor instances dynamically within the `ActorRegistry`.
     */
    private val factories: MutableMap<String, ActorFactory> = mutableMapOf()

    init {
        forever(ActorSystem.conf.registryCleanupEvery) { stopLocalExpired() }
    }

    /**
     * Retrieves an `ActorRef` for the specified actor type and key.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param key A unique string key associated with the actor.
     * @return An `ActorRef` corresponding to the requested actor type and key.
     */
    suspend fun get(clazz: AnyActorClass, key: String): ActorRef = get(clazz, Address.of(clazz, key))

    /**
     * Retrieves an `ActorRef` for the specified actor type and key.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param address The address of the actor.
     * @return An `ActorRef` corresponding to the requested actor type and key.
     */
    open suspend fun get(clazz: AnyActorClass, address: Address): ActorRef = getLocalActor(clazz, address).ref()

    /**
     * Retrieves a local actor instance based on the provided `LocalRef`.
     *
     * This method resolves and returns the actor associated with the given `LocalRef`
     * by utilizing its class type and unique key.
     *
     * @param ref The `LocalRef` representing the actor, containing its class type and address key.
     * @return The actor instance corresponding to the provided `LocalRef`.
     */
    internal suspend fun getLocalActor(ref: LocalRef): AnyActor = getLocalActor(ref.clazz, ref.address)

    /**
     * Retrieves a local actor instance based on the specified actor class and unique key.
     *
     * This method resolves the actor by its class type and key, and ensures that only one instance
     * of the actor is created and stored in the local registry. If the actor is newly created,
     * it will be activated before being returned.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param address The address of the actor.
     * @return The actor instance corresponding to the provided class type and key.
     * @throws IllegalStateException If the ActorSystem is not in a READY state.
     * @throws Exception If an error occurs during the activation of a newly created actor.
     */
    private suspend fun getLocalActor(clazz: AnyActorClass, address: Address): AnyActor {
        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Failed to get $address, ActorSystem is ${ActorSystem.status}.")

        // Limit the concurrent access to one at a time.
        // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
        val (isNew: Boolean, actor: AnyActor) = lock {
            // Check if the actor already exists in the local storage.
            registry[address]?.let { return@lock false to it }

            // Spawn the actor.
            val a: AnyActor = factoryOf(clazz)(address.key)

            // Store the [Actor] to the local storage.
            registry[address] = a

            true to a
        }

        // Only call the activate method if the Actor just created.
        if (isNew) {
            try {
                actor.activate()
                log.debug("Actor {} activated successfully.", address)
            } catch (e: Exception) {
                log.error("Could not activate ${actor.address()}. Reason: ${e.message ?: "Unknown error."}.")
                registry.remove(address)
                throw e
            }
        }

        return actor
    }

    /**
     * Unregisters a local actor reference from the registry.
     *
     * This method removes the actor associated with the provided `LocalRef`
     * from the registry and invalidates the reference to ensure it is no longer
     * linked to the actor.
     *
     * @param ref The `LocalRef` representing the actor to be unregistered.
     * @return Unit The completion of the operation.
     */
    internal suspend fun unregister(ref: LocalRef): Unit = lock {
        // Invalidate the shared LocalRef.
        ref.invalidate()
        // Remove it from the registry.
        registry[ref.address]?.let {
            registry.remove(ref.address)
            log.info("Unregistered actor ${ref.address}.")
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
        log.info("Stopping all local actors (size={})...", registry.size)
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
    fun totalMessages(): Long = registry.map { it.value.stats().receivedMessages }.sum()

    /**
     * Registers a factory function for creating instances of a specific actor type within the ActorRegistry.
     *
     * @param actor The class of the actor to be registered.
     * @param factory A lambda function that takes a string key as a parameter and returns an instance of the actor.
     * @return The updated ActorRegistry instance.
     */
    open fun factoryFor(actor: AnyActorClass, factory: ActorFactory): ActorRegistry {
        if (ActorSystem.status == ActorSystem.Status.READY) error("Cannot register a factory while the system is ready.")
        factories[actor.qualifiedName!!] = factory
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
    private fun factoryOf(actor: AnyActorClass): ActorFactory =
        factories[actor.qualifiedName!!] ?: error("No factory registered for ${actor.qualifiedName!!}.")

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
        log.debug("Stopping local expired actors.")
        registry.values.forEach {
            val df = (Clock.System.now() - it.stats().lastMessageAt).inWholeSeconds
            if (df > ActorSystem.conf.actorExpiresAfter.inWholeSeconds) {
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
    private suspend inline fun <T> lock(f: suspend () -> T): T = mutex.withLock { f() }
}
