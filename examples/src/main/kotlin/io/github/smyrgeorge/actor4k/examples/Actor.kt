package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.actor.types.ManagedActor
import kotlinx.coroutines.runBlocking
import java.util.*

class Actor

data class Request(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Cmd

data class Response(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Reply

fun main(args: Array<String>) {
    val a = object : ManagedActor<Request, Response>() {
        override fun onCmd(cmd: Request): Response {
            log.info { "[$name] Received message: $cmd" }
            return Response(cmd.reqId, "Pong!")
        }
    }

    val a1: ManagedActor<Request, Response> = ActorRegistry.get(a::class.java)
    val a2 = a1.ref()

    runBlocking {
        val cmd1 = Request(msg = "[tell] Hello World!")
        a1.tell(cmd1)

        val cmd2 = Request(msg = "[ask] Ping!")
        val r = a2.ask(cmd2)
        println(r)
    }
}