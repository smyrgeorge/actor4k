package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import kotlinx.coroutines.runBlocking

class ActorTest

data class Request(val msg: String)
data class Response(val msg: String)

data class TestActor(val key: String) : Actor(key) {
    override fun <C> onReceive(cmd: C): Any {
        cmd as Request
        log.info { "[$name] Received message: $cmd" }
        return Response("Pong!")
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val a: Actor.Ref = ActorRegistry.get(TestActor::class, "KEY")

        val cmd1 = Request(msg = "[tell] Hello World!")
        a.tell(cmd1)

        val cmd2 = Request(msg = "[ask] Ping!")
        val r = a.ask<Response>(cmd2)
        println(r)

        val a2: Actor.Ref = ActorRegistry.get(TestActor::class, "KEY")
        a2.tell(cmd1)
    }
}