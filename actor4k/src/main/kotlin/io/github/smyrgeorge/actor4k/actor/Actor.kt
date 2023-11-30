package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration

abstract class Actor<C : Cmd, R : Reply> {

    private val mail = Channel<Patterns<C, R>>(capacity = Channel.UNLIMITED)

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

    suspend fun tell(cmd: C): Unit =
        Patterns.tell<C, R>(cmd).let {
            mail.send(it)
        }

    suspend fun ask(cmd: C): R =
        Patterns.ask<C, R>(cmd).let {
            mail.send(it)
            it.replyTo!!.receive()
        }

    suspend fun ask(cmd: C, timeout: Duration): R =
        withTimeout(timeout) { ask(cmd) }

    private data class Patterns<C : Cmd, R : Reply>(
        val cmd: C,
        val replyTo: Channel<R>? = null
    ) {
        companion object {
            fun <C : Cmd, R : Reply> tell(cmd: C) = Patterns<C, R>(cmd = cmd)
            fun <C : Cmd, R : Reply> ask(cmd: C) = Patterns(cmd = cmd, replyTo = Channel<R>(Channel.RENDEZVOUS))
        }
    }
}