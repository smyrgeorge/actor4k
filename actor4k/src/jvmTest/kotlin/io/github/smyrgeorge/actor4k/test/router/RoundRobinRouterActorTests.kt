package io.github.smyrgeorge.actor4k.test.router

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isNotNull
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.test.util.TestProtocol
import io.github.smyrgeorge.actor4k.test.util.TestRouter
import io.github.smyrgeorge.actor4k.test.util.TestWorker
import io.github.smyrgeorge.actor4k.test.util.TestWorkerThatFailsOnce
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class RoundRobinRouterActorTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
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

        // Send 9 messages - one will fail, but the pattern should continue
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
    fun `ask should work with ROUND_ROBIN strategy`(): Unit = runBlocking {
        // Create workers
        val worker1 = TestWorker()
        val worker2 = TestWorker()

        // Create router with ROUND_ROBIN strategy
        val router = TestRouter(RouterActor.Strategy.ROUND_ROBIN)
            .register(worker1, worker2)

        // Send an ask message
        val result = router.ask(TestProtocol.Ping, 5.seconds)

        // Verify success
        assertThat(result).isSuccess()
        assertThat(result.getOrNull()).isNotNull()
    }
}