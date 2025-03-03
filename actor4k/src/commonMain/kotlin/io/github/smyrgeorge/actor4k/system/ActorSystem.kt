package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem.conf
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.Stats
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.launch
import io.github.smyrgeorge.actor4k.util.extentions.registerShutdownHook
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Represents the Actor System which manages the lifecycle, registration, and configuration
 * of actors and clusters. It is responsible for maintaining the status of actors, collecting
 * statistics, and handling cluster operations.
 */
@Suppress("unused")
object ActorSystem {

    var conf = Conf()
    var type: Type = Type.SIMPLE
    var status: Status = Status.NOT_READY

    lateinit var loggerFactory: Logger.Factory
    val log: Logger by lazy {
        if (!this::loggerFactory.isInitialized) error("Please register a Logger factory.")
        loggerFactory.getLogger(this::class)
    }
    lateinit var stats: Stats
    lateinit var cluster: Cluster
    lateinit var registry: ActorRegistry

    init {
        registerShutdownHook()

        launch {
            while (true) {
                runCatching {
                    delay(conf.clusterCollectStats)
                    stats.collect()
                }
            }
        }

        launch {
            while (true) {
                runCatching {
                    delay(conf.clusterLogStats)
                    // Log [Stats].
                    log.info(stats.toString())
                }
            }
        }
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
     * @param A the type of `Actor` to retrieve.
     * @param actor the `KClass` of the actor type.
     * @param key the unique key identifying the actor.
     * @return the reference (`ActorRef`) associated with the specified actor type and key.
     */
    suspend fun <A : Actor> get(actor: KClass<A>, key: String): ActorRef = registry.get(actor, key)

    /**
     * Configures the ActorSystem with the given configuration.
     *
     * @param conf The configuration object containing parameters for ActorSystem setup, such as queue size,
     * initialization properties, expiration times, and other operational settings.
     * @return The configured instance of the ActorSystem.
     */
    fun conf(conf: Conf): ActorSystem {
        this.conf = conf
        return this
    }

    /**
     * Registers a `Logger.Factory` implementation with the `ActorSystem`.
     *
     * @param loggerFactory The `Logger.Factory` instance used to provide loggers for the system.
     * @return The `ActorSystem` instance for chaining further configurations or operations.
     */
    fun register(loggerFactory: Logger.Factory): ActorSystem {
        this.loggerFactory = loggerFactory
        return this
    }

    /**
     * Registers a `Stats` implementation with the `ActorSystem`.
     *
     * @param stats The `Stats` instance used for statistics collection in the system.
     * @return The `ActorSystem` instance for chaining further configurations or operations.
     */
    fun register(stats: Stats): ActorSystem {
        this.stats = stats
        return this
    }

    /**
     * Registers the provided `ActorRegistry` instance to the current `ActorSystem`.
     *
     * @param registry The instance of `ActorRegistry` to be registered.
     * @return The current `ActorSystem` instance after registering the `ActorRegistry`.
     */
    fun register(registry: ActorRegistry): ActorSystem {
        this.registry = registry
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
        type = Type.CLUSTER
        cluster = c
        return this
    }

    /**
     * Starts the `ActorSystem` after ensuring all necessary components are properly registered
     * and initialized. Validates the state of the system and transitions it to the `READY` status.
     * If the system is configured as a cluster, the cluster's lifecycle is also initiated.
     *
     * @return The initialized and ready-to-use `ActorSystem` instance.
     * @throws IllegalStateException if the logger factory, stats collector, or actor registry
     * is not registered, or if the system is not in the `NOT_READY` status.
     */
    fun start(): ActorSystem {
        if (status != Status.NOT_READY) error("Cannot start cluster while it's $status.")
        if (!this::loggerFactory.isInitialized) error("Please register a Logger factory.")
        if (!this::stats.isInitialized) error("Please register a stats collector.")
        if (!this::registry.isInitialized) error("Please register an actor registry.")
        if (isCluster()) cluster.start()
        status = Status.READY
        return this
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
        status = Status.SHUTTING_DOWN

        log.info("Closing ${registry.size()} actors..")
        registry.shutdown()

        if (isCluster()) {
            log.info("Informing cluster that we are about to leave..")
            cluster.shutdown()
        }

        // Wait for all actors to finish.
        while (registry.size() > 0) {
            log.info("Waiting ${registry.size()} actors to finish.")
            delay(1000)
        }

        // Reset cluster's status.
        status = Status.NOT_READY
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
     * Represents the configuration settings for an actor-based system.
     * This data class allows customization of various parameters related to
     * system performance, initialization processes, resource management,
     * and behavior under specific circumstances.
     *
     * @property actorQueueSize Specifies the size of the actor's queue. It determines how many messages can be queued before the sender is suspended. Defaults to an unlimited capacity.
     * @property initializationRounds The number of rounds for the initialization process. This can affect the readiness and preparation of system components.
     * @property initializationDelayPerRound The delay applied between each initialization round.
     * @property clusterLogStats The interval duration for cluster logging activities.
     * @property clusterCollectStats The frequency at which cluster statistics are collected.
     * @property registryCleanup The frequency of clean-up processes for the registry.
     * @property actorExpiration Duration after which unused actors are considered expired and subject to resource cleanup.
     * @property actorRemoteRefExpiration The expiration time for remote actor references.
     * @property memberManagerRoundDelay The delay between rounds of the member management process.
     * @property lowMemoryThresholdMd Memory threshold in megabytes below which the system may trigger memory warnings or adapt to lower resource conditions.
     */
    data class Conf(
        val actorQueueSize: Int = Channel.UNLIMITED,
        val initializationRounds: Int = 10,
        val initializationDelayPerRound: Duration = 5.seconds,
        val clusterLogStats: Duration = 30.seconds,
        val clusterCollectStats: Duration = 5.seconds,
        val registryCleanup: Duration = 30.seconds,
        val actorExpiration: Duration = 15.minutes,
        val actorRemoteRefExpiration: Duration = 2.minutes,
        val memberManagerRoundDelay: Duration = 2.seconds,
        val lowMemoryThresholdMd: Int = 50
    )
}
