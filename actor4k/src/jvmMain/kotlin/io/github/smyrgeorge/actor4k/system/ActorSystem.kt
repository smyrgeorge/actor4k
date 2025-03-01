package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem.conf
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.Stats
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.system.exitProcess
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

    lateinit var log: Logger
    lateinit var loggerFactory: Logger.Factory
    lateinit var stats: Stats
    lateinit var cluster: Cluster
    lateinit var registry: ActorRegistry

    init {
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
     * Determines whether the current system type is a cluster.
     *
     * This method checks if the current system configuration is set to `Type.CLUSTER`.
     *
     * @return `true` if the system type is `Type.CLUSTER`, otherwise `false`.
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
        if (!this::loggerFactory.isInitialized) error("Please register a Logger factory.")
        log = loggerFactory.getLogger(this::class)
        if (!this::stats.isInitialized) error("Please register a stats collector.")
        if (!this::registry.isInitialized) error("Please register an actor registry.")
        if (status != Status.NOT_READY) error("Cannot start cluster while it's $status.")
        if (isCluster()) cluster.start()
        status = Status.READY
        return this
    }

    enum class Status {
        NOT_READY,
        READY,
        SHUTTING_DOWN
    }

    enum class Type {
        SIMPLE,
        CLUSTER
    }

    data class Conf(
        val actorQueueSize: Int = Channel.UNLIMITED, // Will suspend the senders if the mailbox is full.
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

    private val hook = Runtime.getRuntime().addShutdownHook(
        thread(start = false) {
            runBlocking(Dispatchers.IO) { Shutdown.shutdown(Shutdown.Trigger.EXTERNAL) }
        }
    )

    object Shutdown {

        suspend fun shutdown(triggeredBy: Trigger) {
            log.info("Received shutdown signal by $triggeredBy..")
            status = Status.SHUTTING_DOWN

            log.info("Closing ${registry.count()} actors..")
            registry.shutdown()

            if (isCluster()) {
                log.info("Informing cluster that we are about to leave..")
                cluster.shutdown()
            }

            // Wait for all actors to finish.
            while (registry.count() > 0) {
                log.info("Waiting ${registry.count()} actors to finish.")
                delay(1000)
            }

            when (triggeredBy) {
                Trigger.SELF -> exitProcess(0)
                Trigger.SELF_ERROR -> exitProcess(1)
                Trigger.EXTERNAL -> Unit
            }
        }

        enum class Trigger {
            SELF,
            SELF_ERROR,
            EXTERNAL
        }
    }
}
