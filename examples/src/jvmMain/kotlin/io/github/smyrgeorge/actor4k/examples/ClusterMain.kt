package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor.Message
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

    val registry = ClusterActorRegistry()
        .factoryFor(AccountActor::class) { AccountActor(it) }

    val cluster = ClusterImpl(
        nodes = nodes,
        current = current,
        loggerFactory = loggerFactory,
        routing = {
            // Add extra routing to the underlying HTTP server.
        },
        serialization = {
            polymorphic(Message::class) {
                subclass(Protocol.Req::class, Protocol.Req.serializer())
            }
            polymorphic(Message.Response::class) {
                subclass(Protocol.Req.Resp::class, Protocol.Req.Resp.serializer())
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
        ref.tell(Protocol.Req(message = "[tell] Hello World!"))
        val res = ref.ask<Protocol.Req.Resp>(Protocol.Req(message = "Ping!"))
        println(">>> ask($it): $res")
        delay(1000)
    }

    repeat(2) {
        val echo = cluster.self.echo("Ping!")
        println(">>> echo($it): ${echo.msg}")
        delay(1000)
    }

    delay(2000)
}