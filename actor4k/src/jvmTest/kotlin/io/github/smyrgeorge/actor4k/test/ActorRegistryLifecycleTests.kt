package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotZero
import assertk.assertions.isTrue
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.actor.InitMethodFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.NotRegisteredAccountActor
import io.github.smyrgeorge.actor4k.test.actor.ShortLivedActor
import io.github.smyrgeorge.actor4k.test.actor.SlowInitAccountActor
import io.github.smyrgeorge.actor4k.test.actor.ThrowingDuringMessageProcessingActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.test.util.forEachParallel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails

class ActorRegistryLifecycleTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Should fail for missing actor factory`(): Unit = runBlocking {
        assertFails { ActorSystem.get(NotRegisteredAccountActor::class, ACC0000) }
    }

    @Test
    fun `If init fails the registry get method should also fail`(): Unit = runBlocking {
        assertFails {
            registry.get(InitMethodFailsAccountActor::class, ACC0000)
        }
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `Concurrent gets for the same actor should not create duplicates`(): Unit = runBlocking {
        val workers = listOf(1, 2, 3, 4)
        workers.forEachParallel {
            repeat(100) {
                val ref = registry.get(AccountActor::class, ACC0001)
                ref.tell(Protocol.Req("Ping!"))
            }
        }

        assertThat(registry.size()).isEqualTo(1)
        val ref = registry.get(AccountActor::class, ACC0001)
        val actor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0001)
        delay(1000) // Ensure that all messages are processed.
        assertThat(actor.stats().receivedMessages).isEqualTo(4 * 100)
    }

    @Test
    fun `Registry should remove actor after shutdown`(): Unit = runBlocking {
        // Create an actor
        val ref = registry.get(AccountActor::class, ACC0002)
        assertThat(registry.size()).isEqualTo(1)

        // Shutdown the actor
        val actor = registry.getLocalActor(ref as LocalRef)
        actor.shutdown()

        // Wait for shutdown to complete
        delay(1000)

        // Verify actor is removed from registry
        assertThat(registry.size()).isZero()

        // Verify getting a new actor with same key works
        val newRef = registry.get(AccountActor::class, ACC0002)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(newRef === ref).isFalse()
    }

    @Test
    fun `Registry should track actors properly during system lifecycle`(): Unit = runBlocking {
        // Create multiple actors
        val actorKeys = listOf(ACC0003, ACC0004, ACC0005)

        actorKeys.forEach { key ->
            registry.get(AccountActor::class, key)
        }

        // Verify all actors are in registry
        assertThat(registry.size()).isEqualTo(actorKeys.size)

        // Shutdown system
        ActorSystem.shutdown()
        delay(1000) // Allow time for shutdown

        // Verify registry is empty
        assertThat(registry.size()).isZero()

        // Restart the system for other tests
        ActorSystem.start()
    }

    @Test
    fun `Registry should properly handle actor replacement`(): Unit = runBlocking {
        // Create an actor
        val ref1 = registry.get(AccountActor::class, ACC0006)
        val actor1 = registry.getLocalActor(ref1 as LocalRef)

        // Shutdown the actor
        actor1.shutdown()
        delay(1000)

        // Create new actor with same key
        val ref2 = registry.get(AccountActor::class, ACC0006)

        // Verify it's a different actor instance
        assertThat(ref2 === ref1).isFalse()

        // Verify only one actor with this key exists
        assertThat(registry.size()).isEqualTo(1)
    }

    @Test
    fun `Registry should handle slow initializing actors properly`(): Unit = runBlocking {
        // Test concurrent requests for slow initializing actor
        val results = (1..5).map {
            async {
                try {
                    registry.get(SlowInitAccountActor::class, ACC0009)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }.awaitAll()

        // All requests should succeed
        assertThat(results.all { it }).isTrue()

        // Only one actor should be created
        assertThat(registry.size()).isEqualTo(1)

        // Actor should be properly initialized
        val ref = registry.get(SlowInitAccountActor::class, ACC0009)
        ref.tell(Protocol.Req("Test"))
        delay(500)
        val actor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.stats().receivedMessages).isNotZero()
    }

    @Test
    fun `Registry should handle actor errors during message processing`(): Unit = runBlocking {
        // Create an actor that throws during message processing
        val ref = registry.get(ThrowingDuringMessageProcessingActor::class, ACC0010)

        // Send a message that will cause an exception
        ref.tell(Protocol.Req("THROW"))
        delay(500) // Give time for message processing

        // Actor should remain in registry despite error
        assertThat(registry.size()).isEqualTo(1)

        // Actor should still be able to process messages
        ref.tell(Protocol.Req("Normal"))
        delay(500)

        val actor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.stats().receivedMessages).isEqualTo(2)
    }

    @Test
    fun `Registry should handle short-lived actors properly`(): Unit = runBlocking {
        // Create an actor that will automatically shutdown after processing a message
        val ref = registry.get(ShortLivedActor::class, ACC0011)

        // Send a message that will trigger shutdown
        ref.tell(Protocol.Req("Shutdown"))

        // Give time for processing and shutdown
        delay(1000)

        // Verify actor is removed from registry
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `Registry should be resilient to system restart`(): Unit = runBlocking {
        // Create some actors
        registry.get(AccountActor::class, ACC0012)
        registry.get(AccountActor::class, ACC0013)

        // Verify actors exist
        assertThat(registry.size()).isEqualTo(2)

        // Restart the system
        ActorSystem.shutdown()
        delay(1000)
        ActorSystem.start()

        // Registry should be empty after restart
        assertThat(registry.size()).isZero()

        // Should be able to create new actors
        registry.get(AccountActor::class, ACC0012)
        assertThat(registry.size()).isEqualTo(1)
    }

    @Test
    fun `Registry should handle multiple shutdowns gracefully`(): Unit = runBlocking {
        // Create an actor
        val ref = registry.get(AccountActor::class, ACC0016)
        val actor = registry.getLocalActor(ref as LocalRef)

        // Attempt multiple shutdowns
        actor.shutdown()
        actor.shutdown() // Should not throw
        actor.shutdown() // Should not throw

        // Wait for shutdown
        delay(1000)

        // Registry should be empty
        assertThat(registry.size()).isZero()
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0001: String = "ACC0001"
        private const val ACC0002: String = "ACC0002"
        private const val ACC0003: String = "ACC0003"
        private const val ACC0004: String = "ACC0004"
        private const val ACC0005: String = "ACC0005"
        private const val ACC0006: String = "ACC0006"
        private const val ACC0009: String = "ACC0009"
        private const val ACC0010: String = "ACC0010"
        private const val ACC0011: String = "ACC0011"
        private const val ACC0012: String = "ACC0012"
        private const val ACC0013: String = "ACC0013"
        private const val ACC0016: String = "ACC0016"
    }
}