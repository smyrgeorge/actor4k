package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.*
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class RouterActorTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `RouterActor should fail when no workers are registered`(): Unit = runBlocking {
        // Create router without registering workers
        val router = TestRouter(RouterActor.Strategy.RANDOM)

        // Try to send a message
        val result = router.tell(TestProtocol.Ping)

        // Verify it fails
        assertThat(result).isFailure()
        assertThat(result.exceptionOrNull()).isNotNull().isInstanceOf(IllegalStateException::class)
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
        // Create worker that handles multiple message types
        val worker = TestWorkerWithMultipleMessages()

        // Create router with RANDOM strategy
        val router = TestRouterWithMultipleMessages(RouterActor.Strategy.RANDOM)
            .register(worker)

        // Send different types of messages multiple times
        repeat(5) {
            router.tell(TestProtocolWithMultipleMessages.Ping).getOrThrow()
        }

        repeat(5) {
            router.tell(TestProtocolWithMultipleMessages.Echo("Message $it")).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify all messages were processed
        assertThat(worker.pingCount).isEqualTo(5)
        assertThat(worker.lastEchoMessage).isEqualTo("Message 4") // Last message sent
    }

    @Test
    fun `ROUND_ROBIN strategy should route messages in cyclic order`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2, worker3)

        // Send 9 messages (3 complete cycles)
        repeat(9) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify each worker received exactly 3 messages
        assertThat(worker1.messageCount).isEqualTo(3)
        assertThat(worker2.messageCount).isEqualTo(3)
        assertThat(worker3.messageCount).isEqualTo(3)
    }

    @Test
    fun `BROADCAST strategy should send messages to all workers`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router with BROADCAST strategy
        val router = TestRouter(RouterActor.Strategy.BROADCAST)
            .register(worker1, worker2, worker3)

        // Send 5 messages
        repeat(5) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify each worker received all 5 messages
        assertThat(worker1.messageCount).isEqualTo(5)
        assertThat(worker2.messageCount).isEqualTo(5)
        assertThat(worker3.messageCount).isEqualTo(5)
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
    fun `ask should work with RANDOM strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with RANDOM strategy
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker1, worker2)

        // Send ask message
        val result = router.ask<RouterActor.Protocol.Ok>(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }

    @Test
    fun `ask should work with ROUND_ROBIN strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2)

        // Send ask message
        val result = router.ask<RouterActor.Protocol.Ok>(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }

    @Test
    fun `ask should fail with BROADCAST strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with BROADCAST strategy
        val router = TestRouter(RouterActor.Strategy.BROADCAST)
            .register(worker1, worker2)

        // Send ask message
        val result = router.ask<RouterActor.Protocol.Ok>(TestProtocol.Ping, 5.seconds)

        // Verify failure
        assertThat(result).isFailure()
        assertThat(result.exceptionOrNull()).isNotNull().isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun `ask should work with FIRST_AVAILABLE strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with FIRST_AVAILABLE strategy
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE)
            .register(worker1, worker2)

        // Send ask message
        val result = router.ask<RouterActor.Protocol.Ok>(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }

    @Test
    fun `register should throw exception when called multiple times`(): Unit = runBlocking {
        // Create router and workers
        val router = TestRouter(RouterActor.Strategy.RANDOM)
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // First registration should succeed
        router.register(worker1)

        // Second registration should fail
        val exception = runCatching {
            router.register(worker2)
        }.exceptionOrNull()

        assertThat(exception).isNotNull()
        assertThat(exception!!).isInstanceOf(IllegalStateException::class)
    }

    @Test
    fun `router should shutdown without errors`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router and register workers
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker1, worker2, worker3)

        // Activate the workers by sending a message
        router.tell(TestProtocol.Ping).getOrThrow()

        // Shutdown the router - this should not throw any exceptions
        val result = runCatching {
            router.shutdown()
        }

        // Verify shutdown completed without errors
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `router should work with different message types`(): Unit = runBlocking {
        // Create workers
        val worker = TestWorkerWithMultipleMessages()

        // Create router
        val router = TestRouterWithMultipleMessages(RouterActor.Strategy.RANDOM)
            .register(worker)

        // Send different types of messages
        router.tell(TestProtocolWithMultipleMessages.Ping).getOrThrow()
        router.tell(TestProtocolWithMultipleMessages.Echo("Hello")).getOrThrow()

        delay(500) // Allow time for message processing

        // Verify messages were processed
        assertThat(worker.pingCount).isEqualTo(1)
        assertThat(worker.lastEchoMessage).isEqualTo("Hello")
    }

    @Test
    fun `ROUND_ROBIN strategy should work with varying number of workers`(): Unit = runBlocking {
        // Create different numbers of workers for testing
        val singleWorkerRouter = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(TestWorker())

        val manyWorkersRouter = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(
                TestWorker(), TestWorker(), TestWorker(),
                TestWorker(), TestWorker()
            )

        // Send messages to both routers
        repeat(10) {
            singleWorkerRouter.tell(TestProtocol.Ping).getOrThrow()
            manyWorkersRouter.tell(TestProtocol.Ping).getOrThrow()
        }

        // Both should succeed without errors
        assertThat(true).isTrue() // If we got here, the test passed
    }

    @Test
    fun `ROUND_ROBIN strategy should handle uneven message distribution`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()
        val worker4 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2, worker3, worker4)

        // Send 10 messages (not a multiple of 4)
        repeat(10) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Verify total messages
        val totalMessages = worker1.messageCount + worker2.messageCount +
                worker3.messageCount + worker4.messageCount
        assertThat(totalMessages).isEqualTo(10)

        // Each worker should have received either 2 or 3 messages
        // The exact distribution depends on the initial state of the id counter
        // So we'll check that the distribution is reasonable
        for (worker in listOf(worker1, worker2, worker3, worker4)) {
            assertThat(worker.messageCount).isIn(2, 3)
        }

        // Two workers should have received 3 messages and two should have received 2
        val workersWithThreeMessages = listOf(worker1, worker2, worker3, worker4)
            .count { it.messageCount == 3 }
        val workersWithTwoMessages = listOf(worker1, worker2, worker3, worker4)
            .count { it.messageCount == 2 }

        assertThat(workersWithThreeMessages).isEqualTo(2)
        assertThat(workersWithTwoMessages).isEqualTo(2)
    }

    @Test
    fun `ROUND_ROBIN strategy should maintain pattern with large number of messages`(): Unit = runBlocking {
        // Create workers and track message receipt
        val worker1 = TestWorker()
        val worker2 = TestWorker()
        val worker3 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2, worker3)

        // Send 30 messages (10 complete cycles)
        repeat(30) {
            router.tell(TestProtocol.Ping).getOrThrow()
        }

        delay(500) // Allow time for message processing

        // Each worker should have received exactly 10 messages
        assertThat(worker1.messageCount).isEqualTo(10)
        assertThat(worker2.messageCount).isEqualTo(10)
        assertThat(worker3.messageCount).isEqualTo(10)
    }

    @Test
    fun `ROUND_ROBIN strategy should continue pattern after worker failures`(): Unit = runBlocking {
        // Create a mix of reliable and unreliable workers
        val worker1 = TestWorker()
        val worker2 = TestWorkerThatFailsOnce() // Will fail on first message
        val worker3 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2, worker3)

        // Send 9 messages - one will fail but the pattern should continue
        repeat(9) {
            try {
                router.tell(TestProtocol.Ping).getOrThrow()
            } catch (_: Exception) {
                // Ignore the expected failure
            }
        }

        delay(500) // Allow time for message processing

        // Worker2 should have received 3 messages (one failed, two succeeded)
        // Other workers should have received 3 messages each
        assertThat(worker1.messageCount).isEqualTo(3)
        assertThat(worker2.messageCount).isEqualTo(2) // One failed, so only 2 counted
        assertThat(worker2.attemptCount).isEqualTo(3) // But 3 attempts were made
        assertThat(worker3.messageCount).isEqualTo(3)
    }

    @Test
    fun `router should handle worker failures gracefully`(): Unit = runBlocking {
        // Create a worker that will fail
        val failingWorker = TestWorkerThatFails()

        // Create router with the failing worker
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(failingWorker)

        // Send a message that will cause the worker to fail
        // Use ask instead of tell to get the failure
        val result = router.ask<RouterActor.Protocol.Ok>(TestProtocol.Ping, 5.seconds)

        // The router should propagate the failure
        assertThat(result).isFailure()
        assertThat(result.exceptionOrNull()).isNotNull()
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

    // Test protocol for messages
    sealed class TestProtocol : RouterActor.Protocol() {
        data object Ping : TestProtocol()
    }

    // Test router implementation
    class TestRouter(strategy: Strategy) : RouterActor<TestProtocol, RouterActor.Protocol.Ok>("test-router", strategy)

    // Test worker implementation
    class TestWorker(private val processingTime: Long = 0) :
        RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
        var messageCount = 0
            private set

        override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
            messageCount++
            if (processingTime > 0) {
                delay(processingTime)
            }
            return RouterActor.Protocol.Ok
        }
    }

    // Test protocol with multiple message types
    sealed class TestProtocolWithMultipleMessages : RouterActor.Protocol() {
        data object Ping : TestProtocolWithMultipleMessages()
        data class Echo(val message: String) : TestProtocolWithMultipleMessages()
    }

    // Test router with multiple message types
    class TestRouterWithMultipleMessages(strategy: Strategy) :
        RouterActor<TestProtocolWithMultipleMessages, RouterActor.Protocol.Ok>("test-router-multiple", strategy)

    // Test worker with multiple message types
    class TestWorkerWithMultipleMessages :
        RouterActor.Worker<TestProtocolWithMultipleMessages, RouterActor.Protocol.Ok>() {

        var pingCount = 0
            private set

        var lastEchoMessage: String = ""
            private set

        override suspend fun onReceive(m: TestProtocolWithMultipleMessages): RouterActor.Protocol.Ok {
            when (m) {
                is TestProtocolWithMultipleMessages.Ping -> pingCount++
                is TestProtocolWithMultipleMessages.Echo -> lastEchoMessage = m.message
            }
            return RouterActor.Protocol.Ok
        }
    }

    // Test worker that fails
    class TestWorkerThatFails : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
        override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
            throw RuntimeException("Simulated failure")
        }
    }

    // Test worker that fails occasionally
    class TestWorkerThatFailsOccasionally : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
        var attemptCount: Int = 0
            private set

        var failureCount: Int = 0
            private set

        override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
            attemptCount++

            // Fail on every other message
            if (attemptCount % 2 == 0) {
                failureCount++
                throw RuntimeException("Simulated occasional failure")
            }

            return RouterActor.Protocol.Ok
        }
    }

    // Test worker that fails only on the first message
    class TestWorkerThatFailsOnce : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
        var messageCount: Int = 0
            private set

        var attemptCount: Int = 0
            private set

        override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
            attemptCount++

            // Fail only on the first message
            if (attemptCount == 1) {
                throw RuntimeException("Simulated one-time failure")
            }

            messageCount++
            return RouterActor.Protocol.Ok
        }
    }
}
