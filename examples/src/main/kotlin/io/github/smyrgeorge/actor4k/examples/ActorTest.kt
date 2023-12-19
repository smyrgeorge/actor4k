package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import kotlinx.coroutines.runBlocking

class ActorTest

data class Req(val msg: String)
data class Resp(val msg: String)

data class TestActor(val key: String) : Actor(key) {
    override fun <C> onReceive(cmd: C): Any {
        cmd as Req
        log.info { "[$name] Received message: $cmd" }
        return Resp("Pong!")
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val a: Actor.Ref = ActorRegistry.get(TestActor::class, "KEY")

        val cmd1 = Req(msg = "[tell] Hello World!")
        a.tell(cmd1)

        val cmd2 = Req(msg = "[ask] Ping!")
        val r = a.ask<Resp>(cmd2)
        println(r)

        val a2: Actor.Ref = ActorRegistry.get(TestActor::class, "KEY")
        a2.tell(cmd1)
    }
}