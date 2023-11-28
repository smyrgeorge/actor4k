package io.smyrgeorge.actor4k.examples

import io.smyrgeorge.actor4k.actor.ActorRegistry
import io.smyrgeorge.actor4k.actor.ActorSystem
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import io.smyrgeorge.actor4k.actor.types.ManagedActor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*

class Main

data class Request(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Cmd

data class Response(
    override val reqId: UUID = UUID.randomUUID()
) : Reply

fun main(args: Array<String>) {

    val a = object : ManagedActor<Request, Response>() {
        override fun onCmd(cmd: Request): Response {
            log.info { "Received message: $cmd" }
            return Response(cmd.reqId)
        }
    }

    val a1 = ActorRegistry.get<Request, Reply>(a::class.java)

    runBlocking {
        val cmd = Request(msg = "Hello World!")
        a.tell(cmd)
        delay(5_000)
        val r: Response = a.ask(cmd)
        println(r)
        delay(5_000)
    }
}
