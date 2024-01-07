package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object ActorSystem {

    private val log = KotlinLogging.logger {}

    var status: Status = Status.READY
    var clusterMode: Boolean = false
    lateinit var cluster: Cluster

    fun register(c: Cluster): ActorSystem {
        clusterMode = true
        cluster = c
        return this
    }

    @Suppress("MayBeConstant")
    object Conf {
        val initializationRounds: Int = 10
        val initializationDelayPerRound: Duration = 5.seconds
        val clusterLogStats: Duration = 30.seconds
        val clusterCollectStats: Duration = 10.seconds
        val registryCleanup: Duration = 30.seconds
        val actorExpiration: Duration = 15.minutes
        val actorRemoteRefExpiration: Duration = 2.minutes
        val memberManagerRoundDelay: Duration = 2.seconds
    }

    enum class Status {
        READY,
        SHUTTING_DOWN
    }

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

            if (clusterMode) {
                log.info { "Informing cluster that we are about to leave.." }
                cluster.shutdown()
            }

            // Wait for all actors to finish.
            while (ActorRegistry.count() > 0) {
                log.info { "Waiting ${ActorRegistry.count()} actors to finish." }
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
}
