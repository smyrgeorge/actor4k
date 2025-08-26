package io.github.smyrgeorge.actor4k.test.router

import assertk.assertThat
import assertk.assertions.*
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class RandomRouterActorTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `RANDOM strategy should route messages to a random worker`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker1, worker2, worker3)

        // Send multiple messages
        repeat(10) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify that at least some workers received messages
        // Note: There's a small chance all messages go to one worker, but it's unlikely
        val totalMessages = worker1.messageCount + worker2.messageCount + worker3.messageCount
        assertThat(totalMessages).isEqualTo(10)

        // At least one worker should have received a message
        assertThat(worker1.messageCount > 0 || worker2.messageCount > 0 || worker3.messageCount > 0).isTrue()
    }

    @Test
    fun `RANDOM strategy should distribute load statistically with large number of messages`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker1, worker2, worker3)

        // Send a large number of messages to get better statistical distribution
        repeat(100) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(1000) // Allow time for message processing

        // Verify total messages
        val totalMessages = worker1.messageCount + worker2.messageCount + worker3.messageCount
        assertThat(totalMessages).isEqualTo(100)

        // Each worker should have received a reasonable number of messages
        // With random distribution, it's very unlikely any worker gets less than 10% of messages
        assertThat(worker1.messageCount).isGreaterThan(5)
        assertThat(worker2.messageCount).isGreaterThan(5)
        assertThat(worker3.messageCount).isGreaterThan(5)

        // No worker should have received all messages
        assertThat(worker1.messageCount).isLessThan(100)
        assertThat(worker2.messageCount).isLessThan(100)
        assertThat(worker3.messageCount).isLessThan(100)
    }

    @Test
    fun `RANDOM strategy should work with a single worker`(): Unit = runBlocking {
        // Create a single worker
        val worker = TestWorker()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker)

        // Send multiple messages
        repeat(10) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // The single worker should receive all messages
        assertThat(worker.messageCount).isEqualTo(10)
    }

    @Test
    fun `RANDOM strategy should continue working after worker failures`(): Unit = runBlocking {
        // Create a mix of reliable and unreliable workers
        val reliableWorker = TestWorker()
        val unreliableWorker = TestWorkerThatFailsOnce()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(reliableWorker, unreliableWorker)

        // Send multiple messages - some might fail depending on random selection
        var successCount = 0
        var failureCount = 0

        repeat(20) {
            try {
                router.tell(TestProtocol.Ping).getOrThrow()
                successCount++
            } catch (_: Exception) {
                failureCount++
            }
        }

        delay(500) // Allow time for message processing

        // We should have some successes and possibly some failures
        assertThat(successCount).isGreaterThan(0)

        // The reliable worker should have received some messages
        assertThat(reliableWorker.messageCount).isGreaterThan(0)

        // The unreliable worker should have been attempted at least once
        // and might have succeeded after the first failure
        assertThat(unreliableWorker.attemptCount).isGreaterThan(0)

        // Total processed messages should equal the success count
        // Note: Due to the asynchronous nature, we allow for a small discrepancy
        val totalProcessed = reliableWorker.messageCount + unreliableWorker.messageCount
        assertThat(totalProcessed).isGreaterThanOrEqualTo(successCount - 1)
        assertThat(totalProcessed).isLessThanOrEqualTo(successCount)
    }

    @Test
    fun `RANDOM strategy should handle different message types`(): Unit = runBlocking {
        // Create a worker that handles multiple message types
        val worker = TestWorkerWithMultipleMessages()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker)

        // Send different types of messages multiple times
        repeat(5) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        repeat(5) {
            router.tell(TestProtocol.Echo("Message $it")).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify all messages were processed
        assertThat(worker.pingCount).isEqualTo(5)
        assertThat(worker.lastEchoMessage).isEqualTo("Message 4") // Last message sent
    }

    @Test
    fun `ask should work with RANDOM strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker1, worker2)

        // Send an ask message
        val result = router.ask(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }
}
