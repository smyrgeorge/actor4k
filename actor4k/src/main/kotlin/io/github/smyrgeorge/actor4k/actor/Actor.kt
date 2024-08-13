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
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class Actor(open val shard: String, open val key: String) {
    protected val log = KotlinLogging.logger {}

    private var status = Status.INITIALISING
    private var initializedAt: Instant? = null
    private val name: String = nameOf(this::class.java)
    private val address: String by lazy { addressOf(this::class.java, key) }

    private val stats: Stats = Stats()
    private lateinit var mail: Channel<Patterns>

    /**
     * Is called by the [ActorRegistry].
     * Is called before the [Actor] begins to consume [Message]s.
     * In case of an error the [ActorRegistry] will deregister the newly created [Actor].
     * You can use this hook to early initialise the [Actor]'s state.
     */
    open suspend fun onBeforeActivate() {}

    /**
     * Only is called before the [onReceive] method, only for the first message.
     * You can use this method to lazy initialise the [Actor].
     */
    open suspend fun onActivate(m: Message) {}

    /**
     * Handle the incoming [Message]s.
     */
    abstract suspend fun onReceive(m: Message, r: Response.Builder): Response

    @Suppress("unused")
    suspend fun activate() {
        onBeforeActivate()

        // If activate success, initialise the receive channel.
        mail = Channel(capacity = ActorSystem.conf.actorQueueSize)

        // Set 'READY' status.
        status = Status.READY

        // Start the mail consumer.
        launchGlobal {
            mail.consumeEach {
                // If we get a shutdown event and the actor never initialized successfully,
                // we need to drop all the messages.
                if (initializedAt == null && status.willSoonFinish) return@consumeEach

                stats.last = Instant.now()
                stats.messages += 1

                val msg = Message(stats.messages, it.msg).also { msg ->
                    if (msg.isFirst()) {
                        try {
                            onActivate(msg)
                            initializedAt = Instant.now()
                        } catch (e: ClosedSendChannelException) {
                            log.warn { "[activate] Did not manage to reply in time (although the message was processed). $it" }
                        } catch (e: Exception) {
                            log.error { "[$address] Failed to activate, will shutdown (${e.message ?: ""})" }
                            shutdown()
                            return@consumeEach
                        }
                    }
                }

                val reply: Response = onReceive(msg, Response.Builder())
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> {
                        try {
                            it.replyTo.send(reply.value)
                        } catch (e: ClosedSendChannelException) {
                            log.warn { "[consume] Did not manage to reply in time (although the message was processed). $it" }
                        } catch (e: Exception) {
                            log.error { "[consume] An error occurred while processing $it" }
                        }
                    }
                }
            }
        }
    }

    data class Message(
        val id: Long,
        private val value: Any
    ) {
        @Suppress("UNCHECKED_CAST")
        fun <T> cast(): T = value as? T ?: error("Could not cast to the requested type.")

        fun isFirst(): Boolean = id == 1L
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
        if (!status.canAcceptMessages) error("$address is in status='$status' and thus is not accepting messages.")

        val tell = Patterns.Tell(msg as Any)
        mail.send(tell)
    }

    suspend fun <C, R> ask(msg: C, timeout: Duration = 30.seconds): R {
        if (!status.canAcceptMessages) error("$address is in status='$status' and thus is not accepting messages.")

        val ask = Patterns.Ask(msg as Any)
        return try {
            withTimeout(timeout) {
                mail.send(ask)
                @Suppress("UNCHECKED_CAST")
                ask.replyTo.receive() as? R ?: error("Could not cast to the requested type.")
            }
        } finally {
            ask.replyTo.close()
        }
    }

    fun status(): Status = status
    fun name(): String = name
    fun stats(): Stats = stats
    fun address(): String = address

    fun shutdown() {
        status = Status.FINISHING
        mail.close()
    }

    fun ref(): Ref.Local = Ref.Local(shard, name, key, this::class.java)

    private sealed interface Patterns {
        val msg: Any

        data class Tell(override val msg: Any) : Patterns
        data class Ask(override val msg: Any, val replyTo: Channel<Any> = Channel(Channel.RENDEZVOUS)) : Patterns
    }

    enum class Status(
        val canAcceptMessages: Boolean,
        val willSoonFinish: Boolean
    ) {
        INITIALISING(true, false),
        READY(true, false),
        FINISHING(false, true),
        FINISHED(false, true)
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
            suspend fun stop() = ActorRegistry.get(this).shutdown()
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

    @OptIn(DelicateCoroutinesApi::class)
    private suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit =
        consume {
            for (e in this) action(e)
            if (isClosedForReceive) {
                status = Status.FINISHED
                ActorRegistry.deregister(this@Actor)
            }
        }

    companion object {
        private fun <A : Actor> nameOf(actor: Class<A>): String = actor.simpleName ?: "Anonymous"
        fun <A : Actor> addressOf(actor: Class<A>, key: String): String = addressOf(nameOf(actor), key)
        private fun addressOf(actor: String, key: String): String = "$actor-$key"
    }
}
