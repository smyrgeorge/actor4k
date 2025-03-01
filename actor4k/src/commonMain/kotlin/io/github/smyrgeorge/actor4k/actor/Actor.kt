package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract class representing an actor with basic functionality for message handling,
 * activation, and status management.
 *
 * @property key the key identifying the actor.
 */
abstract class Actor(open val key: String) {
    protected val log: Logger = ActorSystem.loggerFactory.getLogger(this::class)

    private var status = Status.INITIALISING
    private var initializedAt: Instant? = null
    private val name: String = nameOf(this::class)
    private val address: Address by lazy { addressOf(this::class, key) }

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

                stats.last = Clock.System.now()
                stats.messages += 1

                val msg = Message(stats.messages, it.msg)

                // Initialisation flow.
                if (msg.isFirst()) {
                    try {
                        onActivate(msg)
                        // Set 'READY' status.
                        status = Status.READY
                        initializedAt = Clock.System.now()
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

    /**
     * Retrieves the current status of the actor.
     *
     * @return The current status of the actor as a [Status] enum value, which indicates whether
     * the actor can accept messages and if it is in the process of finishing its operations.
     */
    fun status(): Status = status

    /**
     * Retrieves the name of the actor.
     *
     * @return the name of the actor as a [String].
     */
    fun name(): String = name

    /**
     * Retrieves the current statistics of the actor.
     *
     * @return The [Stats] object containing details such as the `last` processed time and the total
     * number of `messages` processed by the actor.
     */
    fun stats(): Stats = stats

    /**
     * Retrieves the address of the actor.
     *
     * @return the address of the actor as a [Address].
     */
    fun address(): Address = address

    /**
     * Initiates the shutdown process for the actor.
     *
     * This function sets the actor's status to `FINISHING` and closes its mailbox,
     * signaling that the actor will no longer process messages.
     */
    fun shutdown() {
        status = Status.FINISHING
        mail.close()
    }

    /**
     * Creates and returns a reference to the current actor as a [LocalRef].
     *
     * The returned [LocalRef] provides details about the actor, including its
     * name, key, and class type, and enables interaction with the actor such
     * as sending messages and querying its status.
     *
     * @return a [LocalRef] representing the reference to the current actor.
     */
    fun ref(): LocalRef = LocalRef(address = address, actor = this::class)

    /**
     * Represents message patterns used by the `Actor` for communication and message handling.
     *
     * This sealed interface defines two types of messages: `Tell` and `Ask`.
     * Each message carries a `msg` property that holds the message payload.
     *
     * - `Tell`: A one-way communication pattern used to send a message without expecting a response.
     * - `Ask`: A request-response communication pattern where a reply is expected.
     *
     * These patterns are consumed and processed within the `Actor` class's message handling logic.
     * Specifically, `Ask` allows sending responses back to the sender using its `replyTo` property.
     */
    private sealed interface Patterns {
        val msg: Any

        /**
         * Represents a one-way communication pattern for sending messages between actors.
         *
         * The `Tell` message pattern encapsulates a payload of type `msg` without requiring
         * or awaiting a response. It is typically used for fire-and-forget communication.
         *
         * This class is part of the sealed interface `Patterns`, which defines the structure
         * for different messaging patterns an actor can use for communication.
         *
         * @property msg The payload of the message being sent.
         */
        data class Tell(
            override val msg: Any
        ) : Patterns

        /**
         * Represents a request-response communication pattern used for interactions between actors.
         *
         * The `Ask` data class encapsulates a message (`msg`) and a channel (`replyTo`) for receiving a response.
         * It is commonly used when a reply is expected from the recipient after message processing.
         *
         * @property msg The payload of the message being sent.
         * @property replyTo A channel used to send the response back to the sender. The channel uses a rendezvous
         * approach to manage communication between the sender and recipient.
         */
        data class Ask(
            override val msg: Any,
            val replyTo: Channel<Result<Any>> = Channel(Channel.RENDEZVOUS)
        ) : Patterns
    }

    /**
     * Represents the various states of an Actor during its lifecycle.
     *
     * Status indicates whether an Actor can accept messages and/or if it is in
     * the process of shutting down. The possible statuses are:
     *
     * - `INITIALISING`: The Actor is being initialized and can accept messages.
     * - `READY`: The Actor is fully initialized and ready to process messages.
     * - `FINISHING`: The Actor is preparing to shut down and will soon stop accepting messages.
     * - `FINISHED`: The Actor has finished its operations and is no longer active.
     *
     * @property canAcceptMessages Indicates whether the Actor is able to accept incoming messages.
     * @property willSoonFinish Indicates whether the Actor is in a transitional state of shutting down.
     */
    enum class Status(
        val canAcceptMessages: Boolean,
        val willSoonFinish: Boolean
    ) {
        INITIALISING(true, false),
        READY(true, false),
        FINISHING(false, true),
        FINISHED(false, true)
    }

    /**
     * Represents the statistical data of an `Actor`.
     *
     * This data class is used to track statistics such as:
     * - The timestamp of the last processed message.
     * - The total number of messages processed by the actor.
     *
     * @property last The timestamp of the last processed message.
     * @property messages The total number of messages processed.
     */
    data class Stats(
        var last: Instant = Clock.System.now(),
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
        /**
         * Retrieves the name of the given actor class.
         *
         * @param actor The class of the actor whose name is to be retrieved.
         * @return The simple name of the actor's class, or "Anonymous" if the name is not available.
         */
        private fun <A : Actor> nameOf(actor: KClass<A>): String = actor.simpleName ?: "Anonymous"

        /**
         * Computes the address of an actor based on its class type and a unique key.
         *
         * @param actor The class type of the actor.
         * @param key A unique key that identifies the actor.
         * @return The computed address of the actor as an [Address] object.
         */
        fun <A : Actor> addressOf(actor: KClass<A>, key: String): Address = Address(nameOf(actor), key)
    }
}
