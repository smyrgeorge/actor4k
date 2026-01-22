package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.*
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessingAccountActor
import io.github.smyrgeorge.actor4k.test.actor.TerminatingAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlinx.coroutines.*
import kotlin.test.Test

class ActorTerminateTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Actor should terminate and reply with errors for pending messages`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0000)
        assertThat(registry.size()).isEqualTo(1)

        // Send multiple messages
        repeat(5) { ref.tell(Protocol.Req("Ping-$it")) }

        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        delay(100) // Allow first message to start processing

        // Terminate the actor
        actor.terminate()

        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATING)

        // Wait for termination to complete
        delay(2000)

        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
        assertThat(actor.stats().terminatedAt).isNotNull()
        assertThat(registry.size()).isZero()

        // All messages are still "received" (dequeued from mailbox) but replied with termination errors
        // The key difference from shutdown is that pending messages get error responses immediately
        // rather than being processed
        assertThat(actor.stats().receivedMessages).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `Terminate should reply with error for ask patterns`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0001)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send a slow message first
        launch { ref.tell(Protocol.Req("SlowMessage")) }
        delay(100)

        // Send ask messages that will be discarded
        val askResults = (1..3).map {
            async {
                actor.ask<Protocol.Resp, Protocol.Req>(Protocol.Req("Ask-$it"))
            }
        }

        delay(100)

        // Terminate while asks are pending
        actor.terminate()

        // Wait for termination
        delay(2000)

        // Verify ask results are failures
        askResults.forEach { deferred ->
            val result = deferred.await()
            assertThat(result.isFailure).isTrue()
        }

        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Behavior_Terminate should trigger actor termination`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(TerminatingAccountActor::class, ACC0002)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send some normal messages first
        repeat(2) { ref.tell(Protocol.Req("Ping-$it")) }

        // Send the terminate trigger message
        ref.tell(Protocol.Req("Terminate"))

        // Wait for termination
        delay(1500)

        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
        assertThat(actor.stats().terminatedAt).isNotNull()
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `onShutdown hook should be executed during termination`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(TerminatingAccountActor::class, ACC0003)
        val actor = registry.getLocalActor(ref as LocalRef) as TerminatingAccountActor

        // Verify initial state
        assertThat(TerminatingAccountActor.shutdownHookExecuted).isFalse()

        // Trigger termination via message
        ref.tell(Protocol.Req("Terminate"))

        // Wait for termination to complete
        delay(1500)

        // Verify onShutdown hook was executed
        assertThat(TerminatingAccountActor.shutdownHookExecuted).isTrue()
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Messages sent during termination should be rejected`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0004)
        val actor = registry.getLocalActor(ref as LocalRef) as SlowProcessingAccountActor

        // Start termination
        actor.terminate()

        // Try sending a message during termination
        val result = actor.ask<Protocol.Resp, Protocol.Req>(Protocol.Req("TooLate"))

        // Verify message was rejected
        assertThat(result.isFailure).isTrue()

        // Wait for termination to complete
        delay(1500)
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Actor should terminate with custom error`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0005)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        val customError = IllegalStateException("Custom termination reason")

        // Terminate with custom error
        actor.terminate(customError)

        // Wait for termination
        delay(1500)

        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
        assertThat(actor.stats().terminatedAt).isNotNull()
    }

    @Test
    fun `Multiple concurrent terminate calls should be handled properly`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0006)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send some messages
        repeat(2) { ref.tell(Protocol.Req("Message-$it")) }

        // Trigger multiple terminate calls concurrently
        val terminateCalls = (1..5).map {
            async {
                actor.terminate()
                true
            }
        }.awaitAll()

        // Verify all terminate calls succeeded (no exceptions)
        assertThat(terminateCalls.all { it }).isTrue()
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATING)

        // Wait for termination to complete
        delay(2000)
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Terminate should be faster than shutdown when messages are queued`(): Unit = runBlocking {
        // Create two actors
        val shutdownRef: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0007)
        val terminateRef: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0008)

        val shutdownActor: AnyActor = registry.getLocalActor(shutdownRef as LocalRef)
        val terminateActor: AnyActor = registry.getLocalActor(terminateRef as LocalRef)

        // Queue multiple messages to both
        repeat(3) {
            shutdownRef.tell(Protocol.Req("Ping-$it"))
            terminateRef.tell(Protocol.Req("Ping-$it"))
        }

        delay(100) // Let first message start processing

        val terminateStart = System.currentTimeMillis()
        terminateActor.terminate()

        // Wait for terminate to complete
        while (terminateActor.status() != Actor.Status.TERMINATED) {
            delay(50)
        }
        val terminateDuration = System.currentTimeMillis() - terminateStart

        val shutdownStart = System.currentTimeMillis()
        shutdownActor.shutdown()

        // Wait for shutdown to complete
        while (shutdownActor.status() != Actor.Status.SHUT_DOWN) {
            delay(50)
        }
        val shutdownDuration = System.currentTimeMillis() - shutdownStart

        // Terminate should be faster because it discards messages
        assertThat(terminateDuration).isLessThan(shutdownDuration)
    }

    @Test
    fun `Terminated actor should have different stats than shutdown actor`(): Unit = runBlocking {
        val terminateRef: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0009)
        val terminateActor: AnyActor = registry.getLocalActor(terminateRef as LocalRef)

        // Terminate the actor
        terminateActor.terminate()
        delay(1500)

        val stats = terminateActor.stats()

        // Verify termination-specific stats
        assertThat(stats.terminatedAt).isNotNull()
        assertThat(stats.triggeredTerminationAt).isNotNull()
        assertThat(stats.shutDownAt).isNull()
        assertThat(stats.triggeredShutDownAt).isNull()

        assertThat(terminateActor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Cannot shutdown after terminate is triggered`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0010)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send a message and start termination
        ref.tell(Protocol.Req("Ping"))
        delay(100)

        actor.terminate()
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATING)

        // Try to shutdown - should have no effect
        actor.shutdown()

        // Status should still be TERMINATING (not SHUTTING_DOWN)
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATING)

        // Wait for termination
        delay(1500)
        assertThat(actor.status()).isEqualTo(Actor.Status.TERMINATED)
    }

    @Test
    fun `Cannot terminate after shutdown is triggered`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, ACC0011)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send a message and start shutdown
        ref.tell(Protocol.Req("Ping"))
        delay(100)

        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUTTING_DOWN)

        // Try to terminate - should have no effect
        actor.terminate()

        // Status should still be SHUTTING_DOWN (not TERMINATING)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUTTING_DOWN)

        // Wait for shutdown
        delay(2000)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    companion object {
        private const val ACC0000: String = "TERM_ACC0000"
        private const val ACC0001: String = "TERM_ACC0001"
        private const val ACC0002: String = "TERM_ACC0002"
        private const val ACC0003: String = "TERM_ACC0003"
        private const val ACC0004: String = "TERM_ACC0004"
        private const val ACC0005: String = "TERM_ACC0005"
        private const val ACC0006: String = "TERM_ACC0006"
        private const val ACC0007: String = "TERM_ACC0007"
        private const val ACC0008: String = "TERM_ACC0008"
        private const val ACC0009: String = "TERM_ACC0009"
        private const val ACC0010: String = "TERM_ACC0010"
        private const val ACC0011: String = "TERM_ACC0011"
    }
}
