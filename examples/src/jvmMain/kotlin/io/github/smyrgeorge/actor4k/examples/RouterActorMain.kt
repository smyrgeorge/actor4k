package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.examples.TestRouterChild.TestProtocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class RoundRobinTestRouter(key: String) :
    RouterActor<TestProtocol>(key, Strategy.ROUND_ROBIN)

class BroadcastDetachedTestRouter() :
    RouterActor<TestProtocol>(randomKey(), Strategy.BROADCAST, true)

class TestRouterChild : RouterActor.Child<TestProtocol>() {
    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        when (m) {
            TestProtocol.Test -> log.info("Received Test message: $m")
        }
        return RouterActor.Protocol.Ok
    }

    sealed class TestProtocol : RouterActor.Protocol() {
        data object Test : TestProtocol()
    }
}


fun main(): Unit = runBlocking {
    val registry = SimpleActorRegistry()
        .factoryFor(RoundRobinTestRouter::class) {
            RoundRobinTestRouter("router-1")
                .register(
                    TestRouterChild(),
                    TestRouterChild(),
                    TestRouterChild()
                )
        }

    ActorSystem
        .register(SimpleLoggerFactory())
        .register(registry)
        .start()

    delay(1000)
    println("RoundRobinTestRouter (attached) router:")
    val r1 = ActorSystem.get(RoundRobinTestRouter::class, "router-1")
    r1.tell(TestProtocol.Test)
    delay(1000)

    delay(1000)
    println("BroadcastTestRouter (detached) router:")
    val r2 = BroadcastDetachedTestRouter()
        .register(
            TestRouterChild(),
            TestRouterChild(),
            TestRouterChild()
        )
    r2.tell(TestProtocol.Test)
    delay(1000)
}