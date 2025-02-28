package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.Stats
import io.github.smyrgeorge.actor4k.util.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    var conf = Conf()

    @Suppress("MemberVisibilityCanBePrivate")
    var type: Type = Type.SIMPLE
    var status: Status = Status.NOT_READY

    lateinit var stats: Stats
    lateinit var cluster: Cluster
    lateinit var registry: ActorRegistry

    init {
        launch {
            while (true) {
                delay(conf.clusterCollectStats)
                stats.collect()
            }
        }

        launch {
            while (true) {
                delay(conf.clusterLogStats)
                // Log [Stats].
                log.info(stats.toString())
            }
        }
    }

    fun isCluster(): Boolean = type == Type.CLUSTER

    suspend fun <A : Actor> get(
        actor: KClass<A>,
        key: String,
        shard: String = key
    ): ActorRef = registry.get(actor.java, key, shard)

    suspend fun <A : Actor> get(
        actor: Class<A>,
        key: String,
        shard: String = key
    ): ActorRef = registry.get(actor, key, shard)

    fun conf(conf: Conf): ActorSystem {
        this.conf = conf
        return this
    }

    fun register(stats: Stats): ActorSystem {
        this.stats = stats
        return this
    }

    fun register(registry: ActorRegistry): ActorSystem {
        this.registry = registry
        return this
    }

    fun register(c: Cluster): ActorSystem {
        if (isCluster()) error("Cannot register a cluster while it's already registered.")
        type = Type.CLUSTER
        cluster = c
        return this
    }

    fun start(): ActorSystem {
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
            registry.stopAll()

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
