package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.util.launchGlobal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("MemberVisibilityCanBePrivate")
object ActorSystem {

    private val log = KotlinLogging.logger {}

    var conf = Conf()
    var type: Type = Type.SIMPLE
    var status: Status = Status.NOT_READY
    var stats: Stats = Stats.Simple()

    lateinit var cluster: Cluster

    init {
        launchGlobal {
            while (true) {
                delay(conf.clusterCollectStats)
                stats.collect()
            }
        }

        launchGlobal {
            while (true) {
                delay(conf.clusterLogStats)
                stats()
            }
        }
    }

    fun stats() {
        // Log [Stats].
        log.info { stats }
    }

    fun register(c: Cluster): ActorSystem {
        if (isCluster()) error("Cannot register a cluster while it is registered")
        type = Type.CLUSTER
        stats = Stats.Cluster()
        cluster = c
        return this
    }

    fun start(c: Conf = Conf()): ActorSystem {
        conf = c
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
        val clusterCollectStats: Duration = 10.seconds,
        val registryCleanup: Duration = 30.seconds,
        val actorExpiration: Duration = 15.minutes,
        val actorRemoteRefExpiration: Duration = 2.minutes,
        val memberManagerRoundDelay: Duration = 2.seconds
    )

    @Suppress("unused")
    private val hook = Runtime.getRuntime().addShutdownHook(
        thread(start = false) { runBlocking(Dispatchers.IO) { Shutdown.shutdown(Shutdown.Trigger.EXTERNAL) } }
    )

    object Shutdown {

        suspend fun shutdown(triggeredBy: Trigger) {
            log.info { "Received shutdown signal by $triggeredBy.." }
            status = Status.SHUTTING_DOWN

            log.info { "Closing ${ActorRegistry.count()} actors.." }
            ActorRegistry.stopAll()

            if (isCluster()) {
                log.info { "Informing cluster that we are about to leave.." }
                cluster.shutdown()
            }

            // Wait for all actors to finish.
            while (ActorRegistry.count() > 0) {
                log.info { "Waiting ${ActorRegistry.count()} actors to finish." }
                // TODO: too large value, delay, with back-of.
                delay(5_000)
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

    fun isCluster(): Boolean = type == Type.CLUSTER
}
