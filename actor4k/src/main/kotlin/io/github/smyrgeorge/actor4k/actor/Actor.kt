package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlin.reflect.KClass
import kotlin.time.Duration

abstract class Actor<C : Cmd, R : Reply>(
    private val key: String
) {

    protected val log = KotlinLogging.logger {}

    val name: String = nameOf(this::class, key)
    private val mail = Channel<Patterns<C, R>>(capacity = Channel.UNLIMITED)

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

    fun ref(): Ref.Local<C, R> = Ref.Local(key = key, name = name, actor = this)

    private data class Patterns<C : Cmd, R : Reply>(
        val cmd: C,
        val replyTo: Channel<R>? = null
    ) {
        companion object {
            fun <C : Cmd, R : Reply> tell(cmd: C) = Patterns<C, R>(cmd = cmd)
            fun <C : Cmd, R : Reply> ask(cmd: C) = Patterns(cmd = cmd, replyTo = Channel<R>(Channel.RENDEZVOUS))
        }
    }

    sealed class Ref<C : Cmd, R : Reply>(
        open val key: String,
        open val name: String
    ) {

        abstract suspend fun tell(cmd: C)
        abstract suspend fun ask(cmd: C): R

        data class Local<C : Cmd, R : Reply>(
            override val key: String,
            override val name: String,
            private val actor: Actor<C, R>
        ) : Ref<C, R>(key, name) {
            override suspend fun tell(cmd: C): Unit = actor.tell(cmd)
            override suspend fun ask(cmd: C): R = actor.ask(cmd)
        }

        data class Remote<C : Cmd, R : Reply>(
            override val key: String,
            override val name: String,
            val node: String
        ) : Ref<C, R>(key, name) {
            override suspend fun tell(cmd: C): Unit = TODO()
//                ActorSystem.cluster.tell(key, Envelope("CHANGE ME"))

            override suspend fun ask(cmd: C): R = TODO()
//                ActorSystem.cluster.ask<R>(key, Envelope("CHANGE ME")).payload
        }
    }

    companion object {

        fun <C : Cmd, R : Reply, A : Actor<C, R>> nameOf(actor: KClass<A>, key: String): String =
            nameOf(actor.java, key)

        fun <C : Cmd, R : Reply, A : Actor<C, R>> nameOf(actor: Class<A>, key: String): String =
            "${actor.simpleName ?: "anonymous"}-$key"
    }
}