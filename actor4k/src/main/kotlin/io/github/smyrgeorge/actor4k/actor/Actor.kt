package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.java.JRef
import io.github.smyrgeorge.actor4k.util.launchGlobal
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class Actor(open val shard: String, open val key: String) {
    protected val log = KotlinLogging.logger {}
    protected val name: String = nameOf(this::class.java)

    private val stats: Stats = Stats()
    private var status = Status.INITIALISING
    private val address: String by lazy { addressOf(this::class.java, key) }
    private val mail = Channel<Patterns>(capacity = ActorSystem.Conf.actorQueueSize)

    @OptIn(DelicateCoroutinesApi::class)
    private suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit =
        consume {
            for (e in this) action(e)
            if (isClosedForReceive) {
                status = Status.FINISHED
                ActorRegistry.deregister(this@Actor)
            }
        }

    open suspend fun onActivate() {}
    abstract suspend fun onReceive(m: Message, r: Response.Builder): Response

    @Suppress("unused")
    suspend fun activate() {
        onActivate()
        status = Status.READY

        launchGlobal {
            mail.consumeEach {
                stats.last = Instant.now()
                stats.messages += 1
                val msg = Message(it.msg)
                val reply = onReceive(msg, Response.Builder())
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> it.replyTo.send(reply.value)
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

    data class Response(
        val value: Any
    ) {
        class Builder {
            private lateinit var value: Any
            fun value(v: Any): Builder {
                value = v
                return this
            }

            fun build(): Response = Response(value)
        }
    }

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

    fun shutdown(cause: Throwable? = null) {
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
        open val shard: String,
        open val name: String,
        open val key: String,
        open val address: String
    ) {
        abstract suspend fun tell(msg: Any)
        abstract suspend fun <R> ask(msg: Any): R

        fun asJava(): JRef = JRef(this)

        data class Local(
            override val shard: String,
            override val name: String,
            override val key: String,
            val actor: Class<out Actor>,
            override val address: String = addressOf(name, key)
        ) : Ref(shard, name, key, address) {
            override suspend fun tell(msg: Any) {
                // Check if the requested shard is locked.
                if (ActorSystem.isCluster()) ShardManager.isLocked(shard)?.ex()
                ActorRegistry.get(this).tell(msg)
            }

            override suspend fun <R> ask(msg: Any): R {
                // Check if the requested shard is locked.
                if (ActorSystem.isCluster()) ShardManager.isLocked(shard)?.ex()
                return ActorRegistry.get(this).ask(msg)
            }

            suspend fun status(): Status = ActorRegistry.get(this).status
            suspend fun stop(cause: Throwable? = null) = ActorRegistry.get(this).shutdown(cause)
        }

        data class Remote(
            override val shard: String,
            override val name: String,
            override val key: String,
            private val clazz: String,
            val exp: Instant,
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

    data class Stats(
        var last: Instant = Instant.now(),
        var messages: Long = 0
    )

    companion object {
        private fun <A : Actor> nameOf(actor: Class<A>): String = actor.simpleName ?: "Anonymous"
        fun <A : Actor> addressOf(actor: Class<A>, key: String): String = addressOf(nameOf(actor), key)
        private fun addressOf(actor: String, key: String): String = "$actor-$key"
    }
}
