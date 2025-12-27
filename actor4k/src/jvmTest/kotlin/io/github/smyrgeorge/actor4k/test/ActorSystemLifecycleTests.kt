package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds

class ActorSystemLifecycleTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        // Ensure that the system is shut down before the suite runs.
        runBlocking { ActorSystem.shutdown() }
    }

    @Test
    fun `Start and shutdown`(): Unit = runBlocking {
        ActorSystem.start()
        ActorSystem.shutdown()
        assertThat(registry.size()).isZero()

        ActorSystem.start()
        ActorSystem.shutdown()
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `Double start should fail`(): Unit = runBlocking {
        ActorSystem.start()
        assertFails { ActorSystem.start() }
        ActorSystem.shutdown()
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `Double shutdown should not fail`(): Unit = runBlocking {
        ActorSystem.start()
        ActorSystem.shutdown()
        assertThat(registry.size()).isZero()
        ActorSystem.shutdown()
    }

    private fun testShutdownWithConf(conf: ActorSystem.Conf, minDuration: Long, maxDuration: Long) = runBlocking {
        ActorSystem.conf(conf)
        ActorSystem.start()
        val start = System.currentTimeMillis()
        ActorSystem.shutdown()
        val duration = System.currentTimeMillis() - start
        assertThat(duration).isGreaterThanOrEqualTo(minDuration)
        assertThat(duration).isLessThan(maxDuration)
    }

    @Test
    fun `Shutdown with custom polling intervals`(): Unit = testShutdownWithConf(
        ActorSystem.Conf(
            shutdownInitialDelay = 10.milliseconds,
            shutdownPollingInterval = 50.milliseconds,
            shutdownFinalDelay = 10.milliseconds
        ),
        20L,
        100L
    )

    @Test
    fun `Shutdown with default polling intervals`(): Unit = testShutdownWithConf(
        ActorSystem.Conf(),
        200L,
        300L
    )
}