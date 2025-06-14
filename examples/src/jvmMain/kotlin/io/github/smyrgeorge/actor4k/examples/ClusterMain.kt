package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.modules.polymorphic

fun main(): Unit = runBlocking {
    val current = ClusterNode.of("node1::localhost:6000")
    val nodes = listOf(current)

    val loggerFactory = SimpleLoggerFactory()

    val registry = ClusterActorRegistry(loggerFactory)
        .factoryFor(AccountActor::class) { AccountActor(it) }

    val cluster = ClusterImpl(
        nodes = nodes,
        current = current,
        registry = registry,
        loggerFactory = loggerFactory,
        routing = {
            // Add extra routing to the underlying HTTP server.
        },
        serialization = {
            polymorphic(Actor.Protocol.Message::class) {
                subclass(Protocol.Ping::class, Protocol.Ping.serializer())
            }
            polymorphic(Actor.Protocol.Response::class) {
                subclass(Protocol.Pong::class, Protocol.Pong.serializer())
            }
        },
    )

    // Start the actor system.
    ActorSystem
        .register(loggerFactory)
        .register(registry)
        .register(cluster)
        .start()

    val ref = registry.get(AccountActor::class, "ACC0010")
    println(">>> ref: $ref")
    repeat(2) {
        ref.tell(Protocol.Ping(message = "[tell] Hello World!"))
        val res = ref.ask(Protocol.Ping(message = "Ping!"))
        println(">>> ask($it): $res")
        delay(1000)
    }

    repeat(2) {
        val echo = cluster.self.echo("Ping!").getOrThrow()
        println(">>> echo($it): ${echo.msg}")
        delay(1000)
    }

    delay(2000)
}