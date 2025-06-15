package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.actor.LongOnShutdownActor
import io.github.smyrgeorge.actor4k.test.actor.ResourceHoldingActor
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessingAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test

class ActorGracefulShutdownTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Actor should graceful shutdown`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0000)
        assertThat(registry.size()).isEqualTo(1)
        repeat(2) { ref.tell(Protocol.Req("Ping!")) }
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        delay(1000)
        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUTTING_DOWN)
        delay(1500)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
        assertThat(actor.stats().shutDownAt).isNotNull()
        assertThat(registry.size()).isZero()
        assertThat(actor.stats().receivedMessages).isEqualTo(2)
    }

    @Test
    fun `onShutdown hook should be executed during graceful shutdown`(): Unit = runBlocking {
        // Use an actor that tracks onShutdown execution
        val ref: ActorRef = ActorSystem.get(LongOnShutdownActor::class, ACC0002)
        val actor = registry.getLocalActor(ref as LocalRef) as LongOnShutdownActor

        // Verify initial state
        assertThat(LongOnShutdownActor.shutdownHookExecuted).isFalse()

        // Trigger shutdown
        actor.shutdown()

        // Give time for onShutdown to complete
        delay(2000)

        // Verify hooks were executed
        assertThat(LongOnShutdownActor.shutdownHookExecuted).isTrue()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    @Test
    fun `System shutdown should gracefully shutdown all actors`(): Unit = runBlocking {
        // Create multiple actors
        val actorCount = 3
        val refs = (0 until actorCount).map { i ->
            ActorSystem.get(SlowProcessingAccountActor::class, "actor-$i")
        }

        // Send messages to each actor
        refs.forEach { ref ->
            repeat(2) { i ->
                ref.tell(Protocol.Req("Message-$i"))
            }
        }

        // Small delay to ensure messages are queued
        delay(100)

        // Shutdown the entire system
        ActorSystem.shutdown()

        // Wait for the graceful shutdown
        delay(3000)

        // Verify all actors are gone
        assertThat(registry.size()).isZero()

        // Restart the system for other tests
        ActorSystem.start()
    }

    @Test
    fun `Actor should release resources during shutdown`(): Unit = runBlocking {
        // Create an actor that holds resources
        val ref: ActorRef = ActorSystem.get(ResourceHoldingActor::class, ACC0003)
        val actor = registry.getLocalActor(ref as LocalRef) as ResourceHoldingActor

        // Use the resource
        ref.tell(Protocol.Req("OpenResource"))
        delay(500)

        // Verify the resource is open
        assertThat(ResourceHoldingActor.resourceClosed).isFalse()

        // Trigger shutdown
        actor.shutdown()

        // Wait for shutdown to complete
        delay(1500)

        // Verify resources were released
        assertThat(ResourceHoldingActor.resourceClosed).isTrue()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    @Test
    fun `Messages sent during shutdown should be rejected`(): Unit = runBlocking {
        // Create an actor
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0004)
        val actor = registry.getLocalActor(ref as LocalRef) as SlowProcessingAccountActor

        // Start shutdown
        actor.shutdown()

        // Try sending a message during shutdown
        val result = actor.ask(Protocol.Req("TooLate"))

        // Verify message was rejected
        assertThat(result.isFailure).isTrue()

        // Wait for shutdown to complete
        delay(2000)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    @Test
    fun `Actor should complete shutdown within timeout`(): Unit = runBlocking {
        // Create an actor that takes a long time to shut down
        val ref: ActorRef = ActorSystem.get(LongOnShutdownActor::class, ACC0005)
        val actor = registry.getLocalActor(ref as LocalRef)

        // Start shutdown with timeout
        val shutdownCompleted = withTimeoutOrNull(500) {
            actor.shutdown()
            while (actor.status() != Actor.Status.SHUT_DOWN) {
                delay(100)
            }
            true
        }

        // Verify shutdown handling
        assertThat(shutdownCompleted).isEqualTo(null) // Should time out

        // Wait for the actual shutdown to complete for cleanup
        delay(2000)
    }

    @Test
    fun `Multiple concurrent shutdown calls should be handled properly`(): Unit = runBlocking {
        // Create an actor
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0006)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send some messages
        repeat(2) { ref.tell(Protocol.Req("Message-$it")) }

        // Trigger multiple shutdown calls concurrently
        val shutdownCalls = (1..5).map {
            async {
                actor.shutdown()
                true
            }
        }.awaitAll()

        // Verify all shutdown calls succeeded
        assertThat(shutdownCalls.all { it }).isTrue()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUTTING_DOWN)

        // Wait for shutdown to complete
        delay(3000)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0002: String = "ACC0002"
        private const val ACC0003: String = "ACC0003"
        private const val ACC0004: String = "ACC0004"
        private const val ACC0005: String = "ACC0005"
        private const val ACC0006: String = "ACC0006"
    }
}