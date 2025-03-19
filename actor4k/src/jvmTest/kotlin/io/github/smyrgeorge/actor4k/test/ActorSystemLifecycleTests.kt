package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails

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
}