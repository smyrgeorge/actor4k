package io.github.smyrgeorge.actor4k.test.router

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.test.util.TestProtocol
import io.github.smyrgeorge.actor4k.test.util.TestRouter
import io.github.smyrgeorge.actor4k.test.util.TestWorker
import io.github.smyrgeorge.actor4k.test.util.TestWorkerThatFails
import io.github.smyrgeorge.actor4k.test.util.TestWorkerWithMultipleMessages
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
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(worker)

        // Send different types of messages
        router.tell(TestProtocol.Ping).getOrThrow()
        router.tell(TestProtocol.Echo("Hello")).getOrThrow()

        delay(500) // Allow time for message processing

        // Verify messages were processed
        assertThat(worker.pingCount).isEqualTo(1)
        assertThat(worker.lastEchoMessage).isEqualTo("Hello")
    }

    @Test
    fun `router should handle worker failures gracefully`(): Unit = runBlocking {
        // Create a worker that will fail
        val failingWorker = TestWorkerThatFails()

        // Create a router with the failing worker
        val router = TestRouter(RouterActor.Strategy.RANDOM)
            .register(failingWorker)

        // Send a message that will cause the worker to fail
        // Use ask instead of tell to get the failure
        val result = router.ask(TestProtocol.Ping, 5.seconds)

        // The router should propagate the failure
        assertThat(result).isFailure()
        assertThat(result.exceptionOrNull()).isNotNull()
    }
}
