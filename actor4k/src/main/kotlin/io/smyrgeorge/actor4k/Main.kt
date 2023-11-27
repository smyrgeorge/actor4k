package io.smyrgeorge.actor4k

import io.smyrgeorge.actor4k.actor.AbstractActor
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*

class Main

data class CmdA(
    override val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Cmd

data class ReplyA(
    override val reqId: UUID = UUID.randomUUID()
) : Reply

fun main(args: Array<String>) {

    val a = object : AbstractActor<CmdA, ReplyA>() {
        override fun onCmd(cmd: CmdA): ReplyA {
            log.info { "Received message: $cmd" }
            return ReplyA(cmd.reqId)
        }
    }

    runBlocking {
        val cmd = CmdA(msg = "Hello World!")
        a.tell(cmd)
        delay(5_000)
        val r: ReplyA = a.ask(cmd)
        println(r)
        delay(5_000)
    }
}
