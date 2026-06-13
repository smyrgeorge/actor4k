package io.github.smyrgeorge.actor4k.test.router

import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class DeadlockVerifyTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    // Worker that shuts itself down after processing one message (Behavior.Shutdown => no afterReceive).
    class SelfShutdownWorker : RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {
        var count = 0
            private set
        override suspend fun onReceive(m: TestProtocol): Behavior<TestProtocol.Ok> {
            count++
            return Behavior.Shutdown()
        }
    }

    @Test
    fun `SCENARIO A worker exhaustion - tell blocks forever after all workers self-shutdown`(): Unit = runBlocking {
        val w1 = SelfShutdownWorker()
        val w2 = SelfShutdownWorker()
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE).register(w1, w2)

        // Two messages consume the two seeded tokens; both workers self-shutdown (no afterReceive => no re-register).
        router.tell(TestProtocol.Ping).getOrThrow()
        router.tell(TestProtocol.Ping).getOrThrow()
        delay(200) // let both workers process & shutdown

        // Third tell: available is now empty, no worker will ever re-register.
        val result = withTimeoutOrNull(3.seconds) {
            router.tell(TestProtocol.Ping).getOrThrow()
            "completed"
        }
        println(">>> SCENARIO A result=$result (null means it BLOCKED past 3s = deadlock)")
        println(">>> router status after = ${router.status()}")
    }

    @Test
    fun `SCENARIO B router shutdown then tell`(): Unit = runBlocking {
        val w1 = TestWorker()
        val w2 = TestWorker()
        val router = TestRouter(RouterActor.Strategy.FIRST_AVAILABLE).register(w1, w2)

        router.shutdown()
        delay(200)
        println(">>> SCENARIO B router status after shutdown = ${router.status()}")
        println(">>> w1 status=${w1.status()} w2 status=${w2.status()}")

        val result = withTimeoutOrNull(3.seconds) {
            router.tell(TestProtocol.Ping)
        }
        println(">>> SCENARIO B tell result=$result (null means it BLOCKED = deadlock)")
    }
}
