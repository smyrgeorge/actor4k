package io.smyrgeorge.actor4k

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smyrgeorge.actor4k.actor.AbstractActor
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*

class Main

private val log = KotlinLogging.logger {}

data class CmdA(
    val reqId: UUID = UUID.randomUUID(),
    val msg: String
) : Cmd

data class ReplyA(
    val reqId: UUID = UUID.randomUUID()
) : Reply

fun main(args: Array<String>) {
    log.info { "Hello World" }

    val a = object : AbstractActor<CmdA, ReplyA>() {}

    runBlocking {
        val cmd = CmdA(msg = "Hello World!")
        a.tell(cmd)
        delay(5_000)
    }
}
