package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.examples.TestRouterWorker.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class RoundRobinTestRouter(key: String) : RouterActor<Protocol, Protocol.Ok>(key, Strategy.ROUND_ROBIN)
class BroadcastDetachedTestRouter() : RouterActor<Protocol, Protocol.Ok>(randomKey(), Strategy.BROADCAST)

class TestRouterWorker : RouterActor.Worker<Protocol, Protocol.Ok>() {
    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Ok> {
        when (m) {
            Protocol.Test -> log.info("[${address()}] Received Test message: $m")
        }
        return Behavior.Respond(Protocol.Ok)
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data object Test : Message<Ok>()
        data object Ok : Response()
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
    r1.tell(Protocol.Test).getOrThrow()
    delay(1000)

    delay(1000)
    println("BroadcastTestRouter (detached) router:")
    val r2 = BroadcastDetachedTestRouter()
        .register(
            TestRouterWorker(),
            TestRouterWorker(),
            TestRouterWorker()
        )

    r2.tell(Protocol.Test).getOrThrow()
    r2.ask(Protocol.Test).getOrThrow()
    delay(1000)
}