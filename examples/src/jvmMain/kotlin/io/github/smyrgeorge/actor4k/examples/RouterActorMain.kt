package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor.Protocol
import io.github.smyrgeorge.actor4k.examples.TestRouterWorker.TestWorkerProtocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class RoundRobinTestRouter(key: String) :
    RouterActor<TestWorkerProtocol, Protocol.Ok>(key, Strategy.ROUND_ROBIN)

class BroadcastDetachedTestRouter() :
    RouterActor<TestWorkerProtocol, Protocol.Ok>(randomKey(), Strategy.BROADCAST)

class TestRouterWorker : RouterActor.Worker<TestWorkerProtocol, Protocol.Ok>() {
    override suspend fun onReceive(m: TestWorkerProtocol): Protocol.Ok {
        when (m) {
            TestWorkerProtocol.Test -> log.info("[${address()}] Received Test message: $m")
        }
        return Protocol.Ok
    }

    sealed class TestWorkerProtocol : Protocol() {
        data object Test : TestWorkerProtocol()
    }
}

fun main(): Unit = runBlocking {
    val loggerFactory = SimpleLoggerFactory()

    val registry = SimpleActorRegistry(loggerFactory)
        .factoryFor(RoundRobinTestRouter::class) {
            RoundRobinTestRouter(key = it)
                .register(
                    TestRouterWorker(),
                    TestRouterWorker(),
                    TestRouterWorker()
                )
        }

    ActorSystem
        .register(loggerFactory)
        .register(registry)
        .start()

    delay(1000)
    println("RoundRobinTestRouter (attached) router:")
    val r1 = ActorSystem.get(RoundRobinTestRouter::class, "router-1")
    r1.tell(TestWorkerProtocol.Test).getOrThrow()
    delay(1000)

    delay(1000)
    println("BroadcastTestRouter (detached) router:")
    val r2 = BroadcastDetachedTestRouter()
        .register(
            TestRouterWorker(),
            TestRouterWorker(),
            TestRouterWorker()
        )

    r2.tell(TestWorkerProtocol.Test).getOrThrow()
    delay(1000)
}