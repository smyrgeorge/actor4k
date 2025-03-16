package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem.conf
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.Stats
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import io.github.smyrgeorge.actor4k.util.extentions.forever
import io.github.smyrgeorge.actor4k.util.extentions.registerShutdownHook
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the Actor System which manages the lifecycle, registration, and configuration
 * of actors and clusters. It is responsible for maintaining the status of actors, collecting
 * statistics, and handling cluster operations.
 */
@Suppress("unused")
object ActorSystem {

    private var _conf = Conf()
    private var _type: Type = Type.SIMPLE
    private var _status: Status = Status.NOT_READY
    private lateinit var _stats: Stats
    private lateinit var _cluster: Cluster
    private lateinit var _registry: ActorRegistry
    private lateinit var _loggerFactory: Logger.Factory

    private val log: Logger by lazy {
        if (!this::_loggerFactory.isInitialized) error("Please register a Logger factory.")
        loggerFactory.getLogger(this::class)
    }

    val conf: Conf get() = _conf
    val type: Type get() = _type
    val status: Status get() = _status
    val stats: Stats get() = _stats
    val cluster: Cluster get() = _cluster
    val registry: ActorRegistry get() = _registry
    val loggerFactory: Logger.Factory get() = _loggerFactory

    init {
        registerShutdownHook()
        forever(_conf.systemCollectStatsEvery) { stats.collect() }
        forever(_conf.systemLogStatsEvery) { log.info(stats.toString()) }
    }

    /**
     * Determines whether the current actor system is configured as a cluster.
     *
     * This method checks if the `type` of the actor system is `CLUSTER`, indicating
     * that the system is operating in a distributed cluster mode with cluster-specific
     * functionalities enabled.
     *
     * @return `true` if the actor system is configured as a cluster, `false` otherwise.
     */
    fun isCluster(): Boolean = type == Type.CLUSTER

    /**
     * Retrieves an `ActorRef` instance from the registry for a specific actor type and key.
     *
     * @param actor the `KClass` of the actor type.
     * @param key the unique key identifying the actor.
     * @return the reference (`ActorRef`) associated with the specified actor type and key.
     */
    suspend fun get(actor: AnyActorClass, key: String): ActorRef = registry.get(actor, key)

    /**
     * Configures the ActorSystem with the given configuration.
     *
     * @param conf The configuration object containing parameters for ActorSystem setup, such as queue size,
     * initialization properties, expiration times, and other operational settings.
     * @return The configured instance of the ActorSystem.
     */
    fun conf(conf: Conf): ActorSystem {
        _conf = conf
        return this
    }

    /**
     * Registers a `Logger.Factory` implementation with the `ActorSystem`.
     *
     * @param loggerFactory The `Logger.Factory` instance used to provide loggers for the system.
     * @return The `ActorSystem` instance for chaining further configurations or operations.
     */
    fun register(loggerFactory: Logger.Factory): ActorSystem {
        _loggerFactory = loggerFactory
        return this
    }

    /**
     * Registers a `Stats` implementation with the `ActorSystem`.
     *
     * @param stats The `Stats` instance used for statistics collection in the system.
     * @return The `ActorSystem` instance for chaining further configurations or operations.
     */
    fun register(stats: Stats): ActorSystem {
        _stats = stats
        return this
    }

    /**
     * Registers the provided `ActorRegistry` instance to the current `ActorSystem`.
     *
     * @param registry The instance of `ActorRegistry` to be registered.
     * @return The current `ActorSystem` instance after registering the `ActorRegistry`.
     */
    fun register(registry: ActorRegistry): ActorSystem {
        _registry = registry
        return this
    }

    /**
     * Registers a cluster with the current actor system.
     *
     * This method configures the actor system to operate in a cluster environment and initializes
     * the provided cluster instance. Attempting to register a cluster when the system is already
     * set as a cluster will result in an error.
     *
     * @param c The cluster instance to be registered with the actor system.
     * @return The updated actor system configured for cluster mode.
     * @throws IllegalStateException If the system is already registered as a cluster.
     */
    fun register(c: Cluster): ActorSystem {
        if (isCluster()) error("Cannot register a cluster while it's already registered.")
        _type = Type.CLUSTER
        _cluster = c
        return this
    }

    /**
     * Starts the `ActorSystem` after ensuring all necessary components are properly registered
     * and initialized. Validates the state of the system and transitions it to the `READY` status.
     * If the system is configured as a cluster, the cluster's lifecycle is also initiated.
     *
     * @throws IllegalStateException if the logger factory, stats collector, or actor registry
     * is not registered, or if the system is not in the `NOT_READY` status.
     *
     * @param wait If `true`, the method blocks until the server is fully started.
     *             If `false`, it returns immediately after invoking the server's start mechanism.
     */
    fun start(wait: Boolean = false) {
        if (status != Status.NOT_READY) error("Cannot start cluster while it's $status.")
        if (!this::_loggerFactory.isInitialized) error("Please register a Logger factory.")
        if (!this::_stats.isInitialized) error("Please register a stats collector.")
        if (!this::_registry.isInitialized) error("Please register an actor registry.")
        log.info("Starting actor system...")
        _status = Status.READY
        if (isCluster()) cluster.start(wait)
    }

    /**
     * Initiates the shutdown process for the system.
     *
     * The shutdown process includes:
     * - Logging the shutdown initiation and updating the status to `SHUTTING_DOWN`.
     * - Initiating the shutdown of all locally registered actors within the registry.
     * - If the system is part of a cluster, informing the cluster of the shutdown process and triggering cluster-specific shutdown tasks.
     * - Periodically checking and logging the remaining number of active actors until all have finished their shutdown processes.
     *
     * This method ensures an orderly and safe termination of the system, completing all necessary cleanup tasks.
     * It is designed to handle local and clustered environments appropriately.
     */
    suspend fun shutdown() {
        log.info("Received shutdown signal, will shutdown...")
        _status = Status.SHUTTING_DOWN

        registry.shutdown()

        if (isCluster()) cluster.shutdown()

        // Wait for all actors to finish.
        delay(100.milliseconds)
        while (registry.size() > 0) {
            log.info("Waiting ${registry.size()} actors to finish...")
            delay(1.seconds)
        }

        // Reset cluster's status.
        _status = Status.NOT_READY
        log.info("Shutdown complete.")
    }

    /**
     * Represents the various states of an actor system during its lifecycle.
     *
     * This enum is used to monitor and manage the status of the system through different phases:
     *
     * - `NOT_READY`: Indicates that the system is not yet initialized and cannot perform operations.
     * - `READY`: Denotes that the system is fully initialized and operational.
     * - `SHUTTING_DOWN`: Represents the state where the system is in the process of shutting down,
     *   ensuring safe termination and cleanup of resources.
     *
     * These statuses are integral to ensuring the smooth functioning of the system and are typically
     * checked before executing certain operations or transitioning between system states.
     */
    enum class Status {
        NOT_READY,
        READY,
        SHUTTING_DOWN
    }

    /**
     * Represents the types of actor system configurations.
     *
     * This enum defines the operational mode of an actor system, which can be either:
     * - `SIMPLE`: A basic configuration without cluster capabilities.
     * - `CLUSTER`: A distributed configuration that enables cluster-based functionalities.
     *
     * The type determines the behavior and capabilities of the actor system,
     * including whether it supports distributed clustering features.
     */
    enum class Type {
        SIMPLE,
        CLUSTER
    }

    /**
     * Configuration class for setting up system parameters in an Actor-based system.
     *
     * @property actorQueueSize Specifies the maximum size of the actor's message queue. Defaults to unlimited queue size.
     * @property actorAskTimeout Defines the timeout duration for requests sent using the 'ask' pattern to an actor.
     * @property actorExpiresAfter Specifies the expiration time for inactive actors, determining when they are removed from the system.
     * @property systemCollectStatsEvery Specifies the interval for system-level statistic collection.
     * @property systemLogStatsEvery Configures the time interval for system statistics logging.
     * @property registryCleanupEvery Specifies the interval for cleaning up the actor registry to remove expired or unused entries.
     */
    data class Conf(
        val actorQueueSize: Int = Channel.UNLIMITED,
        val actorAskTimeout: Duration = 30.seconds,
        val actorExpiresAfter: Duration = 5.minutes,
        val systemCollectStatsEvery: Duration = 5.seconds,
        val systemLogStatsEvery: Duration = 30.seconds,
        val registryCleanupEvery: Duration = 30.seconds,
    )
}
