package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import kotlinx.coroutines.runBlocking
import java.util.*

class ActorTest

data class Request(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Cmd

data class Response(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Reply

data class TestActor(val key: String) : Actor<Request, Response>(key) {
    override fun onCmd(cmd: Request): Response {
        log.info { "[$name] Received message: $cmd" }
        return Response(cmd.reqId, "Pong!")
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val a: Actor.Ref<Request, Response> = ActorRegistry.get(TestActor::class, "KEY")

        val cmd1 = Request(msg = "[tell] Hello World!")
        a.tell(cmd1)

        val cmd2 = Request(msg = "[ask] Ping!")
        val r = a.ask(cmd2)
        println(r)

        val a2: Actor.Ref<Request, Response> = ActorRegistry.get(TestActor::class, "KEY")
        a2.tell(cmd1)
    }
}