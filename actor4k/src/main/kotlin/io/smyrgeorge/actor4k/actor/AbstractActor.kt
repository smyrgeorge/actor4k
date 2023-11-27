package io.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

abstract class AbstractActor<C : Cmd, R : Reply> {

    private val mail = Channel<InternalCmd<C, R>>(capacity = Channel.UNLIMITED)

    protected val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            mail.consumeEach {
                val reply: R = onCmd(it.cmd)
                it.replyTo?.send(reply)
            }
        }
    }

    abstract fun onCmd(cmd: C): R

    suspend fun tell(cmd: C): Unit = InternalCmd.tell<C, R>(cmd).let {
        mail.send(it)
    }

    suspend fun ask(cmd: C): R = InternalCmd.ask<C, R>(cmd).let {
        mail.send(it)
        it.replyTo!!.receive()
    }

    private data class InternalCmd<C : Cmd, R : Reply>(
        val cmd: C,
        val replyTo: Channel<R>? = null
    ) {
        companion object {
            fun <C : Cmd, R : Reply> tell(cmd: C) = InternalCmd<C, R>(cmd = cmd)
            fun <C : Cmd, R : Reply> ask(cmd: C) = InternalCmd(cmd = cmd, replyTo = Channel<R>(Channel.RENDEZVOUS))
        }
    }
}