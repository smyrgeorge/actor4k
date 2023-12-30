package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.system.exitProcess

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

    object Conf {
        val clusterLogStats: Duration = Duration.ofSeconds(5)
        val registryCleanup: Duration = Duration.ofSeconds(60)
        val actorExpiration: Duration = Duration.ofMinutes(15)
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