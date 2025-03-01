package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.launch
import io.github.smyrgeorge.actor4k.util.Logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract class representing an actor with basic functionality for message handling,
 * activation, and status management.
 *
 * @property shard the shard identifier for the actor.
 * @property key the key identifying the actor.
 */
abstract class Actor(open val shard: String, open val key: String) {
    protected val log: Logger = ActorSystem.loggerFactory.getLogger(this::class)

    private var status = Status.INITIALISING
    private var initializedAt: Instant? = null
    private val name: String = nameOf(this::class)
    private val address: String by lazy { addressOf(this::class, key) }

    private val stats: Stats = Stats()
    private val mail: Channel<Patterns> = Channel(capacity = ActorSystem.conf.actorQueueSize)

    /**
     * Is called by the [SimpleActorRegistry].
     * Is called before the [Actor] begins to consume [Message]s.
     * In case of an error the [SimpleActorRegistry] will deregister the newly created [Actor].
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

    suspend fun activate() {
        onBeforeActivate()

        // Start the mail consumer.
        launch {
            mail.consumeEach {
                // Case that initialization flow failed and we still have messages to consume.
                // If we get a shutdown event and the actor never initialized successfully,
                // we need to drop all the messages.
                if (initializedAt == null && status.willSoonFinish) {
                    if (it is Patterns.Ask) {
                        try {
                            val e = IllegalStateException("Actor is prematurely closed (could not be initialized).")
                            it.replyTo.send(Result.failure(e))
                        } catch (_: Exception) {
                            log.debug("Could not send reply to the client.")
                        }
                    }
                    return@consumeEach
                }

                stats.last = Instant.now()
                stats.messages += 1

                val msg = Message(stats.messages, it.msg)

                // Initialisation flow.
                if (msg.isFirst()) {
                    try {
                        onActivate(msg)
                        // Set 'READY' status.
                        status = Status.READY
                        initializedAt = Instant.now()
                    } catch (e: Exception) {
                        // In case of an error we need to close the [Actor] immediately.
                        log.error("[$address] Failed to activate, will shutdown (${e.message ?: ""})")
                        if (it is Patterns.Ask) {
                            try {
                                // We should be able to reply immediately.
                                withTimeout(2.seconds) {
                                    it.replyTo.send(Result.failure(e))
                                }
                            } catch (e: Exception) {
                                log.debug("Could not send reply to the client. ${e.message}")
                            }
                        }
                        shutdown()
                        return@consumeEach
                    }
                }

                // Consume flow.
                val reply: Result<Response> = runCatching { onReceive(msg, Response.Builder()) }
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> {
                        try {
                            val r: Result<Any> =
                                if (reply.isSuccess) Result.success(reply.getOrThrow().value)
                                else Result.failure(reply.exceptionOrNull()!!)
                            // We should be able to reply immediately.
                            withTimeout(2.seconds) {
                                it.replyTo.send(r)
                            }
                        } catch (e: TimeoutCancellationException) {
                            log.debug("[consume] Could not send reply in time. ${e.message}")
                        } catch (_: ClosedSendChannelException) {
                            log.warn("[consume] Did not manage to reply in time (although the message was processed). $it")
                        } catch (_: Exception) {
                            log.error("[consume] An error occurred while processing $it")
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

    /**
     * Sends a message to the actor.
     *
     * @param msg the message to be sent
     */
    suspend fun <C> tell(msg: C) {
        if (!status.canAcceptMessages) error("$address is in status='$status' and thus is not accepting messages.")
        val tell = Patterns.Tell(msg as Any)
        mail.send(tell)
    }

    /**
     * Sends a message to the actor and waits for a response.
     *
     * @param msg the message to be sent
     * @param timeout the timeout duration for waiting for a response (default is 30 seconds)
     * @return the response from the actor
     */
    suspend fun <C, R> ask(msg: C, timeout: Duration = 30.seconds): R {
        if (!status.canAcceptMessages) error("$address is in status='$status' and thus is not accepting messages.")
        val ask = Patterns.Ask(msg as Any)
        return try {
            withTimeout(timeout) {
                mail.send(ask)
                val reply = ask.replyTo.receive().getOrThrow()
                @Suppress("UNCHECKED_CAST")
                reply as? R ?: error("Could not cast to the requested type.")
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

    fun ref(): LocalRef = LocalRef(shard, name, key, this::class)

    private sealed interface Patterns {
        val msg: Any

        data class Tell(
            override val msg: Any
        ) : Patterns

        data class Ask(
            override val msg: Any,
            val replyTo: Channel<Result<Any>> = Channel(Channel.RENDEZVOUS)
        ) : Patterns
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


    data class Stats(
        var last: Instant = Instant.now(),
        var messages: Long = 0
    )

    @OptIn(DelicateCoroutinesApi::class)
    private suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit =
        consume {
            for (e in this) {
                try {
                    action(e)
                } catch (e: Exception) {
                    log.warn("[$address] An error occurred while processing. ${e.message ?: ""}")
                }
            }
            if (isClosedForReceive) {
                status = Status.FINISHED
                ActorSystem.registry.unregister(this@Actor)
            }
        }

    companion object {
        private fun <A : Actor> nameOf(actor: KClass<A>): String = actor.simpleName ?: "Anonymous"

        /**
         * Calculates the address of an actor by concatenating the actor name with the given key.
         *
         * @param actor the class type of the actor
         * @param key the key used to generate the address
         * @return the address of the actor
         */
        fun <A : Actor> addressOf(actor: KClass<A>, key: String): String = addressOf(nameOf(actor), key)

        /**
         * Calculates the address of an actor by concatenating the actor name with the given key.
         *
         * @param actor the actor's name
         * @param key the key used to generate the address
         * @return the address of the actor
         */
        fun addressOf(actor: String, key: String): String = "$actor-$key"
    }
}
