package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
                val msg = Message(it.msg)
                val reply = onReceive(msg)
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> it.replyTo.send(reply)
                }
            }
        }
    }

    data class Message(
        private val value: Any
    ) {
        @Suppress("UNCHECKED_CAST")
        fun <T> cast(): T = value as? T ?: error("Could not cast to the requested type.")
    }

    abstract fun onReceive(m: Message): Any

    suspend fun <C> tell(msg: C): Unit =
        Patterns.Tell(msg as Any).let { mail.send(it) }

    suspend fun <C, R> ask(msg: C, timeout: Duration = 30.seconds): R =
        withTimeout(timeout) {
            Patterns.Ask(msg as Any).let {
                mail.send(it)
                @Suppress("UNCHECKED_CAST")
                it.replyTo.receive() as? R ?: error("Could not cast to the requested type.")
            }
        }

    fun ref(): Ref.Local = Ref.Local(name = name, key = key, actor = this)

    private sealed interface Patterns {
        val msg: Any

        data class Tell(override val msg: Any) : Patterns
        data class Ask(override val msg: Any, val replyTo: Channel<Any> = Channel(Channel.RENDEZVOUS)) : Patterns
    }

    sealed class Ref(
        open val name: String,
        open val key: String
    ) {
        abstract suspend fun tell(msg: Any)
        abstract suspend fun <R> ask(msg: Any): R

        data class Local(
            override val name: String,
            override val key: String,
            private val actor: Actor
        ) : Ref(name, key) {
            override suspend fun tell(msg: Any): Unit = actor.tell(msg)
            override suspend fun <R> ask(msg: Any): R = actor.ask(msg)
        }

        data class Remote(
            override val name: String,
            override val key: String,
            val clazz: String,
            val node: String
        ) : Ref(name, key) {
            override suspend fun tell(msg: Any) {
                val actor: String = addressOf(name, key)
                val payload: ByteArray = ActorSystem.cluster.serde.encode(msg)
                val message = Envelope.Tell(clazz, key, payload, msg::class.java.canonicalName)
                ActorSystem.cluster.msg<Envelope.Response>(actor, message)
            }

            override suspend fun <R> ask(msg: Any): R {
                val actor: String = addressOf(name, key)
                val payload: ByteArray = ActorSystem.cluster.serde.encode(msg)
                val message = Envelope.Ask(clazz, key, payload, msg::class.java.canonicalName)
                val res = ActorSystem.cluster.msg<Envelope.Response>(actor, message)
                return ActorSystem.cluster.serde.decode(res.payloadClass, res.payload)
            }
        }
    }

    companion object {
        private fun <A : Actor> nameOf(actor: Class<A>): String = actor.simpleName ?: "Anonymous"
        fun <A : Actor> addressOf(actor: Class<A>, key: String): String = addressOf(nameOf(actor), key)
        private fun addressOf(actor: String, key: String): String = "$actor-$key"
    }
}