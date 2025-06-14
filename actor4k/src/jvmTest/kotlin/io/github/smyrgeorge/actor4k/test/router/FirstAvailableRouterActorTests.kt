package io.github.smyrgeorge.actor4k.test.router

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isSuccess
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.test.util.TestProtocol
import io.github.smyrgeorge.actor4k.test.util.TestRouter
import io.github.smyrgeorge.actor4k.test.util.TestWorker
import io.github.smyrgeorge.actor4k.test.util.TestWorkerThatFailsOccasionally
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class FirstAvailableRouterActorTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `FIRST_AVAILABLE strategy should route messages to available workers`(): Unit = runBlocking {
        // Create workers with different processing times
        val fastWorker = TestWorker(processingTime = 100)
        val slowWorker = TestWorker(processingTime = 500)

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(fastWorker, slowWorker)

        // Send 5 messages in quick succession
        repeat(5) {
            router.tell(TestProtocol.Ping).getOrThrow()
            delay(50) // Small delay between sends
        }

        delay(1000) // Allow time for all message processing

        // Fast worker should have processed more messages than slow worker
        assertThat(fastWorker.messageCount).isGreaterThan(slowWorker.messageCount)

        // Total messages should be 5
        assertThat(fastWorker.messageCount + slowWorker.messageCount).isEqualTo(5)
    }

    @Test
    fun `FIRST_AVAILABLE strategy should distribute load based on worker processing speed`(): Unit = runBlocking {
        // Create workers with very different processing times
        val veryFastWorker = TestWorker(processingTime = 50)
        val mediumWorker = TestWorker(processingTime = 200)
        val slowWorker = TestWorker(processingTime = 500)

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(veryFastWorker, mediumWorker, slowWorker)

        // Send many messages in quick succession
        repeat(20) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(2000) // Allow time for all message processing

        // Very fast worker should process significantly more messages than others
        assertThat(veryFastWorker.messageCount).isGreaterThan(mediumWorker.messageCount)
        assertThat(mediumWorker.messageCount).isGreaterThan(slowWorker.messageCount)

        // Total messages should be 20
        assertThat(veryFastWorker.messageCount + mediumWorker.messageCount + slowWorker.messageCount).isEqualTo(20)
    }

    @Test
    fun `FIRST_AVAILABLE strategy should handle all workers being busy`(): Unit = runBlocking {
        // Create workers with long processing times
        val worker1 = TestWorker(processingTime = 300)
        val worker2 = TestWorker(processingTime = 300)

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(worker1, worker2)

        // Send messages to occupy all workers
        router.tell(TestProtocol.Ping).getOrThrow()
        router.tell(TestProtocol.Ping).getOrThrow()

        // Send another message - this should wait until a worker becomes available
        val startTime = System.currentTimeMillis()
        router.tell(TestProtocol.Ping).getOrThrow()
        val endTime = System.currentTimeMillis()

        // The third message should have waited for at least one worker to become available
        // which should take approximately 300ms
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(250)

        delay(500) // Allow time for all message processing

        // Total messages should be 3
        assertThat(worker1.messageCount + worker2.messageCount).isEqualTo(3)
    }

    @Test
    fun `FIRST_AVAILABLE strategy should recover when workers fail`(): Unit = runBlocking {
        // Create a mix of reliable and unreliable workers
        val reliableWorker = TestWorker()
        val unreliableWorker = TestWorkerThatFailsOccasionally()

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(reliableWorker, unreliableWorker)

        // Send multiple messages
        repeat(10) {
            // Use tell which doesn't propagate failures
            router.tell(TestProtocol.Ping).getOrThrow()
            delay(50) // Small delay between sends
        }

        delay(1000) // Allow time for message processing

        // The reliable worker should have processed some messages
        assertThat(reliableWorker.messageCount).isGreaterThan(0)

        // The unreliable worker should have attempted to process some messages
        assertThat(unreliableWorker.attemptCount).isGreaterThan(0)

        // Some messages should have failed (in the unreliable worker)
        assertThat(unreliableWorker.failureCount).isGreaterThan(0)
    }

    @Test
    fun `FIRST_AVAILABLE strategy should work with a single worker`(): Unit = runBlocking {
        // Create a single worker
        val worker = TestWorker()

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(worker)

        // Send multiple messages
        repeat(5) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // The worker should have processed all messages
        assertThat(worker.messageCount).isEqualTo(5)
    }

    @Test
    fun `ask should work with FIRST_AVAILABLE strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(worker1, worker2)

        // Send an ask message
        val result = router.ask(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }
}