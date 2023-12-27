package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.concurrent.thread

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
    private val shutdown = Runtime.getRuntime().addShutdownHook(thread(start = false) {
        runBlocking(Dispatchers.IO) {
            log.info { "Received shutdown signal.." }
            status = Status.SHUTTING_DOWN

            log.info { "Closing ${ActorRegistry.count()} actors.." }
            ActorRegistry.stopAll()

            if (clusterMode) {
                log.info { "Informing cluster that we are about to leave.." }
                cluster.shutdown()
            }

            // Wait for all actors to finish.
            while (ActorRegistry.count() > 0) {
                delay(5_000)
            }
        }
    })
}