package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSuccess
import assertk.assertions.isTrue
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
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

    // Test protocol for messages
    sealed class TestProtocol : RouterActor.Protocol() {
        data object Ping : TestProtocol()
    }

    // Test router implementation
    class TestRouter(strategy: Strategy) : RouterActor<TestProtocol, RouterActor.Protocol.Ok>("test-router", strategy)

    // Test worker implementation
    class TestWorker(private val processingTime: Long = 0) : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
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
}