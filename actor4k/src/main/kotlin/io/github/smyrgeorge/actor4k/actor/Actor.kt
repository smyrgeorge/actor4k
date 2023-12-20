package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration

abstract class Actor(
    private val key: String
) {

    protected val log = KotlinLogging.logger {}

    val name: String = nameOf(this::class.java)
    private val mail = Channel<Patterns>(capacity = Channel.UNLIMITED)

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            mail.consumeEach {
                val reply = onReceive(it.cmd)
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> it.replyTo.send(reply)
                }
            }
        }
    }

    abstract fun <C> onReceive(cmd: C): Any

    suspend fun <C> tell(cmd: C): Unit =
        Patterns.Tell(cmd as Any).let { mail.send(it) }

    suspend fun <C, R> ask(cmd: C): R =
        Patterns.Ask(cmd as Any).let {
            mail.send(it)
            @Suppress("UNCHECKED_CAST")
            it.replyTo.receive() as? R ?: error("Could not cast to the requested type.")
        }

    suspend fun <C, R> ask(cmd: C, timeout: Duration): R =
        withTimeout(timeout) { ask(cmd) }

    fun ref(): Ref.Local = Ref.Local(name = name, key = key, actor = this)

    private sealed interface Patterns {
        val cmd: Any

        data class Tell(override val cmd: Any) : Patterns
        data class Ask(override val cmd: Any, val replyTo: Channel<Any> = Channel(Channel.RENDEZVOUS)) : Patterns
    }

    sealed class Ref(
        open val name: String,
        open val key: String
    ) {
        abstract suspend fun tell(cmd: Any)
        abstract suspend fun <R> ask(cmd: Any): R

        data class Local(
            override val name: String,
            override val key: String,
            private val actor: Actor
        ) : Ref(name, key) {
            override suspend fun tell(cmd: Any): Unit = actor.tell(cmd)
            override suspend fun <R> ask(cmd: Any): R = actor.ask(cmd)
        }

        data class Remote(
            override val name: String,
            override val key: String,
            val clazz: String,
            val node: String
        ) : Ref(name, key) {
            override suspend fun tell(cmd: Any) {
                val actor: String = addressOf(name, key)
                val payload: ByteArray = ActorSystem.cluster.serde.encode(cmd)
                val message = Envelope.Tell(clazz, key, payload, cmd::class.java.canonicalName)
                ActorSystem.cluster.msg<Envelope.Response>(actor, message)
            }

            override suspend fun <R> ask(cmd: Any): R {
                val actor: String = addressOf(name, key)
                val payload: ByteArray = ActorSystem.cluster.serde.encode(cmd)
                val message = Envelope.Ask(clazz, key, payload, cmd::class.java.canonicalName)
                val res = ActorSystem.cluster.msg<Envelope.Response>(actor, message)
                val clazz: Class<*> = ActorSystem.cluster.serde.loadClass(res.payloadClass)
                return ActorSystem.cluster.serde.decode(clazz, res.payload)
            }
        }
    }

    companion object {
        private fun <A : Actor> nameOf(actor: Class<A>): String = actor.simpleName ?: "Anonymous"
        fun <A : Actor> addressOf(actor: Class<A>, key: String): String = addressOf(nameOf(actor), key)
        private fun addressOf(actor: String, key: String): String = "$actor-$key"
    }
}