package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.shard.Shard
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class Actor(
    private val shard: Shard.Key,
    private val key: Key
) {

    protected val log = KotlinLogging.logger {}
    protected val name: String = nameOf(this::class.java)

    private val stats: Stats = Stats()
    private var status = Status.INITIALISING
    private val address: String = addressOf(this::class.java, key)
    private val mail = Channel<Patterns>(capacity = Channel.UNLIMITED)

    @OptIn(DelicateCoroutinesApi::class)
    private suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit =
        consume {
            for (e in this) action(e)
            if (isClosedForReceive) {
                status = Status.FINISHED
                ActorRegistry.unregister(this@Actor::class.java, key)
            }
        }

    init {
        // TODO: add initialization hooks.
        status = Status.READY

        //  TODO: Block consumer until actor is initialized.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            mail.consumeEach {
                stats.last = Instant.now()
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

    suspend fun <C> tell(msg: C) {
        if (status != Status.READY) error("$address is in status='$status' and thus is not accepting messages.")
        Patterns.Tell(msg as Any).let { mail.send(it) }
    }

    suspend fun <C, R> ask(msg: C, timeout: Duration = 30.seconds): R {
        if (status != Status.READY) error("$address is in status='$status' and thus is not accepting messages.")
        return withTimeout(timeout) {
            Patterns.Ask(msg as Any).let {
                mail.send(it)
                @Suppress("UNCHECKED_CAST")
                it.replyTo.receive() as? R ?: error("Could not cast to the requested type.")
            }
        }
    }

    fun status(): Status = status
    fun stats(): Stats = stats
    fun address(): String = address

    fun stop(cause: Throwable? = null) {
        status = Status.FINISHING
        mail.close(cause)
    }

    fun ref(): Ref.Local = Ref.Local(shard, name, key, this::class.java)

    private sealed interface Patterns {
        val msg: Any

        data class Tell(override val msg: Any) : Patterns
        data class Ask(override val msg: Any, val replyTo: Channel<Any> = Channel(Channel.RENDEZVOUS)) : Patterns
    }

    enum class Status {
        INITIALISING,
        READY,
        FINISHING,
        FINISHED
    }

    sealed class Ref(
        open val shard: Shard.Key,
        open val name: String,
        open val key: Key,
        open val address: String
    ) {
        abstract suspend fun tell(msg: Any)
        abstract suspend fun <R> ask(msg: Any): R

        data class Local(
            override val shard: Shard.Key,
            override val name: String,
            override val key: Key,
            val actor: Class<out Actor>,
            override val address: String = addressOf(name, key)
        ) : Ref(shard, name, key, address) {
            override suspend fun tell(msg: Any): Unit =
                ActorRegistry.get(this).tell(msg)

            override suspend fun <R> ask(msg: Any): R =
                ActorRegistry.get(this).ask(msg)

            suspend fun status(): Status = ActorRegistry.get(this).status
            suspend fun stop(cause: Throwable? = null) = ActorRegistry.get(this).stop(cause)
        }

        data class Remote(
            override val shard: Shard.Key,
            override val name: String,
            override val key: Key,
            private val clazz: String,
            override val address: String = addressOf(name, key)
        ) : Ref(shard, name, key, address) {
            override suspend fun tell(msg: Any) {
                val payload: ByteArray = ActorSystem.cluster.serde.encode(msg::class.java, msg)
                val message = Envelope.Tell(shard, clazz, key, payload, msg::class.java.name)
                ActorSystem.cluster.msg(message).getOrThrow<String>()
            }

            override suspend fun <R> ask(msg: Any): R {
                val payload: ByteArray = ActorSystem.cluster.serde.encode(msg::class.java, msg)
                val message = Envelope.Ask(shard, clazz, key, payload, msg::class.java.name)
                return ActorSystem.cluster.msg(message).getOrThrow()
            }
        }
    }

    @Serializable
    data class Key(val value: String)

    data class Stats(var last: Instant = Instant.now())

    companion object {
        private fun <A : Actor> nameOf(actor: Class<A>): String = actor.simpleName ?: "Anonymous"
        fun <A : Actor> addressOf(actor: Class<A>, key: Key): String = addressOf(nameOf(actor), key)
        private fun addressOf(actor: String, key: Key): String = "$actor-${key.value}"
    }
}