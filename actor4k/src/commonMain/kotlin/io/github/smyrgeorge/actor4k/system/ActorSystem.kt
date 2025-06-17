package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem.conf
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats
import io.github.smyrgeorge.actor4k.system.stats.Stats
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import io.github.smyrgeorge.actor4k.util.extentions.defaultDispatcher
import io.github.smyrgeorge.actor4k.util.extentions.forever
import io.github.smyrgeorge.actor4k.util.extentions.registerShutdownHook
import kotlinx.coroutines.CoroutineDispatcher
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
    private var _stats: Stats = SimpleStats()
    private var _dispatcher: CoroutineDispatcher = defaultDispatcher
    private lateinit var _cluster: Cluster
    private lateinit var _registry: ActorRegistry
    private lateinit var _loggerFactory: Logger.Factory

    private lateinit var _log: Logger
    private val log: Logger
        get() = if (!this::_log.isInitialized) error("Please register a Logger factory.") else _log

    /**
     * Indicates whether the logging system has been initiated.
     *
     * This variable is used to ensure that the logging-related operations,
     * such as starting the stat collector and periodic logging of system
     * statistics, are initialized only once during the lifecycle of the
     * actor system.
     *
     * When set to `true`, it denotes that the stat collector and other
     * periodic logging mechanisms have already been started, preventing
     * further redundant initializations.
     */
    private var loggingStarted = false

    val conf: Conf get() = _conf
    val type: Type get() = _type
    val status: Status get() = _status
    val stats: Stats get() = _stats
    val dispatcher: CoroutineDispatcher get() = _dispatcher
    val cluster: Cluster get() = if (!this::_cluster.isInitialized) error("Please register a cluster.") else _cluster
    val registry: ActorRegistry get() = if (!this::_registry.isInitialized) error("Please register an actor registry.") else _registry
    val loggerFactory: Logger.Factory get() = if (!this::_loggerFactory.isInitialized) error("Please register a Logger factory.") else _loggerFactory

    init {
        registerShutdownHook()
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
        _log = loggerFactory.getLogger(this::class)
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
     * Registers a provided CoroutineDispatcher to the ActorSystem.
     *
     * @param dispatcher The CoroutineDispatcher to be assigned for execution control.
     * @return The current instance of the ActorSystem.
     */
    fun register(dispatcher: CoroutineDispatcher): ActorSystem {
        _dispatcher = dispatcher
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
        if (!this::_registry.isInitialized) error("Please register an actor registry.")

        log.info("Starting actor system...")

        // Start the stats collector once per JVM instance.
        if (!loggingStarted) {
            loggingStarted = true
            log.info("Starting stats collector...")
            forever(_conf.systemCollectStatsEvery) {
                if (status != Status.READY) return@forever
                stats.collect()
            }
            forever(_conf.systemLogStatsEvery) {
                if (status != Status.READY) return@forever
                log.info(stats.toString())
            }
        }

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
        if (status != Status.READY) return

        log.info("Received shutdown signal, will shutdown...")
        _status = Status.SHUTTING_DOWN

        // Shutdown the actor registry.
        registry.shutdown()

        // Shutdown the cluster.
        if (isCluster()) cluster.shutdown()

        // Wait for all actors to finish.
        delay(100.milliseconds)
        var remainingActors = registry.size()
        while (remainingActors > 0) {
            log.info("Waiting $remainingActors actors to finish...")
            delay(1.seconds)
            registry.shutdown()
            remainingActors = registry.size()
        }

        // Reset cluster's status.
        _status = Status.NOT_READY
        log.info("Shutdown complete.")
        delay(100.milliseconds)
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
     * Represents the configuration parameters for an Actor System.
     *
     * This class is used to customize the behavior and operational settings of the Actor System
     * such as queue size, timeouts, expiration settings, and system statistics.
     *
     * @property actorQueueSize The size of the actor's message queue. Default is unlimited.
     * @property actorAskTimeout The maximum timeout duration for ask operations from actors.
     * @property actorReplyTimeout The maximum time a system will wait for an actor to reply.
     * @property actorActivateTimeout The timeout duration for actor activation steps.
     * @property actorShutdownHookTimeout The timeout duration for actor cleanup during system shutdown.
     * @property actorExpiresAfter The duration after which inactive actors will be considered expired.
     * @property systemCollectStatsEvery The interval for collecting system statistics.
     * @property systemLogStatsEvery The interval for logging system statistics to the output.
     * @property registryCleanupEvery The frequency at which the actor registry is cleaned up.
     */
    data class Conf(
        val actorQueueSize: Int = Channel.UNLIMITED,
        val actorAskTimeout: Duration = 30.seconds,
        val actorReplyTimeout: Duration = 2.seconds,
        val actorActivateTimeout: Duration = 5.seconds,
        val actorShutdownHookTimeout: Duration = 10.seconds,
        val actorExpiresAfter: Duration = 5.minutes,
        val systemCollectStatsEvery: Duration = 5.seconds,
        val systemLogStatsEvery: Duration = 30.seconds,
        val registryCleanupEvery: Duration = 30.seconds,
    )
}
