package io.github.smyrgeorge.actor4k.test.router

import assertk.assertThat
import assertk.assertions.*
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.*
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class BroadcastRouterActorTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
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
}