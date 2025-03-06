package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

    private val stats: Stats = Stats()
    private var status = Status.ACTIVATING
    private var initializationFailed: Exception? = null
    private val address: Address by lazy { Address.of(this::class, key) }
    private val mail: Channel<Patterns> = Channel(capacity = ActorSystem.conf.actorQueueSize)

    /**
     * Hook called before the actor is activated.
     *
     * This method provides a pre-activation step for setting up or preparing
     * the actor before it processes its first message. It is executed within
     * the `activate` method, prior to handling the initialization message and
     * before the actor enters its active state.
     *
     * Override this method in a subclass to implement any custom logic
     * required before the actor's activation.
     *
     * This function suspends and allows for asynchronous operations to be
     * performed during the pre-activation phase.
     */
    open suspend fun onBeforeActivate() {}

    /**
     * Handles the activation process of the actor upon receiving the first message.
     *
     * This method is invoked after the `onBeforeActivate` hook and before the actor
     * begins processing subsequent messages. It allows initializing or setting up
     * the actor's state based on the provided initial message.
     *
     * @param m The initial message used to activate and initialize the actor.
     */
    open suspend fun onActivate(m: Message) {}

    /**
     * Processes an incoming message and generates a response.
     *
     * This method is responsible for handling the content of the incoming `Message` and producing
     * a corresponding `Response` using the provided `Response.Builder`. It is called for each
     * message the `Actor` consumes after it has been activated.
     *
     * @param m the incoming message to be processed.
     * @param r the builder for constructing the response.
     * @return the constructed response after processing the message.
     */
    abstract suspend fun onReceive(m: Message, r: Response.Builder): Response

    /**
     * Activates the actor and starts the message consumption process.
     *
     * This function is responsible for initializing the actor, consuming incoming messages,
     * and handling them according to the defined patterns (`Tell` or `Ask`).
     * It performs the following tasks:
     *
     * - Invokes the `onBeforeActivate` function for any pre-activation setup.
     * - Processes initialization messages to activate the actor using `onActivate`.
     * - Handles subsequent messages using the `onReceive` method.
     * - Manages any errors during initialization by replying with an error or shutting down the actor.
     * - Updates the actor's statistics (`stats`) with the processing timestamp and message count.
     *
     * The method processes messages in two flows:
     * 1. Initialization Flow: Processes the first message to initialize the actor.
     * 2. Consumption Flow: Handles all subsequent messages after initialization.
     *
     * In case of failures during activation, the actor is immediately shut down,
     * and appropriate error messages are sent in response to pending or incoming requests.
     *
     * This function suspends and starts a coroutine to listen and process messages
     * from the actor's mailbox until shutdown.
     */
    suspend fun activate() {
        onBeforeActivate()

        // Start the mail consumer.
        launch {
            mail.consumeEach {
                // Case that activation flow failed and we still have messages to consume.
                // If we get a shutdown event and the actor never initialized successfully,
                // we need to reply with an error and to drop all the messages.
                if (initializationFailed != null) {
                    replyActivationError(it)
                    return@consumeEach
                }

                stats.lastMessageAt = Clock.System.now()
                stats.processedMessages += 1

                val msg = Message(stats.processedMessages, it.msg)

                // Activation flow.
                if (msg.isFirst()) {
                    try {
                        onActivate(msg)
                        // Set 'READY' status.
                        status = Status.READY
                        stats.initializedAt = Clock.System.now()
                    } catch (e: Exception) {
                        // In case of an error we need to close the [Actor] immediately.
                        log.error("[$address::activate] Failed to activate, will shutdown (${e.message ?: ""})")
                        initializationFailed = e
                        replyActivationError(it)
                        shutdown()
                        return@consumeEach
                    }
                }

                // Consume flow.
                val reply: Result<Response> = runCatching { onReceive(msg, Response.Builder()) }
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> {
                        val r: Result<Any> =
                            if (reply.isSuccess) Result.success(reply.getOrThrow().value)
                            else Result.failure(reply.exceptionOrNull()!!)
                        reply(operation = "consume", pattern = it, reply = r)
                    }
                }
            }
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
     * Sends a reply to an actor using a specified message pattern, handling timeouts and closed channels.
     *
     * This function attempts to send the response to the `replyTo` channel within a timeout of 2 seconds.
     * It also logs warnings in case of timeout, closed channel, or other exceptions.
     *
     * @param operation The operation name associated with the reply, used for logging.
     * @param pattern The `Ask` pattern containing the message payload and the channel for sending a response.
     * @param reply The result to be sent as a reply, encapsulated in a `Result` type.
     */
    private suspend fun reply(operation: String, pattern: Patterns.Ask, reply: Result<Any>) {
        try {
            // We should be able to reply immediately.
            withTimeout(2.seconds) {
                pattern.replyTo.send(reply)
            }
        } catch (_: TimeoutCancellationException) {
            log.warn("[$address::$operation] Could not reply in time. {}")
        } catch (_: ClosedSendChannelException) {
            log.warn("[$address::$operation] Could not reply, the channel is closed.")
        } catch (e: Exception) {
            log.warn("[$address::$operation] Could not reply. {}", e.message ?: "Unknown error.")
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
    fun name(): String = address.name

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
        stats.shutDownAt = Clock.System.now()
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
     * Represents a message with an identifier and a value.
     *
     * This class is primarily utilized in actor-based systems to encapsulate messages
     * exchanged between actors. Each message is identified by a unique identifier
     * and can hold a value of any type.
     *
     * @property id Unique identifier of the message.
     * @property value The value or payload of the message, which can be of any type.
     */
    data class Message(
        val id: Long,
        private val value: Any
    ) {
        /**
         * Casts the encapsulated value of the message to the specified type.
         *
         * This function attempts to cast the payload of the message (`value`) to the requested
         * type `T`. If the cast is unsuccessful, an error is thrown indicating
         * that the value cannot be cast to the desired type.
         *
         * @return The payload of the message cast to the specified type `T`.
         * @throws IllegalStateException if the value cannot be cast to the requested type.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> cast(): T = value as? T ?: error("Could not cast to the requested type.")

        /**
         * Determines if the current message is the first one.
         *
         * @return `true` if the message's identifier (`id`) is `1L`, indicating it is the first message; `false` otherwise.
         */
        fun isFirst(): Boolean = id == 1L
    }

    /**
     * Represents a response returned from an actor after processing a message.
     * This class encapsulates a result value of type `Any`.
     *
     * The `Response` class has a companion `Builder` class, which allows for
     * a step-by-step construction of a `Response` object using a fluent API.
     *
     * @property value The encapsulated value of the response, which can be of any type.
     */
    data class Response(
        val value: Any
    ) {
        /**
         * A builder class for constructing instances of the `Response` class.
         *
         * This class enables a step-by-step, fluent approach to setting the properties
         * of a `Response` object before creating an instance of it. The primary method of the
         * builder is `value`, which assigns a value to the `Response`. To complete the building
         * process, the `build` method is used to generate the `Response` instance.
         *
         * Methods:
         * - `value(v: Any): Builder`: Sets the value for the `Response` to be built and returns the builder instance for chaining.
         * - `build(): Response`: Constructs a `Response` instance using the value set in the builder.
         */
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
     * - `ACTIVATING`: The Actor is being initialized and can accept messages.
     * - `READY`: The Actor is fully initialized and ready to process messages.
     * - `FINISHING`: The Actor is preparing to shut down and will soon stop accepting messages.
     * - `FINISHED`: The Actor has finished its operations and is no longer active.
     *
     * @property canAcceptMessages Indicates whether the Actor is able to accept incoming messages.
     */
    enum class Status(
        val canAcceptMessages: Boolean
    ) {
        ACTIVATING(true),
        READY(true),
        FINISHING(false),
        FINISHED(false)
    }

    /**
     * Represents the statistical data and lifecycle timestamps of an actor.
     *
     * This class captures important metrics for monitoring an actor's activity,
     * such as its creation time, the time it was initialized, the last time it processed
     * a message, and when it was shut down. It also keeps track of the total number
     * of messages processed by the actor.
     *
     * @property createdAt The timestamp when the actor was created.
     * @property initializedAt The timestamp when the actor was initialized, or `null` if not yet initialized.
     * @property shutDownAt The timestamp when the actor was shut down, or `null` if not yet shut down.
     * @property lastMessageAt The timestamp of the last message processed by the actor.
     * @property processedMessages The total number of messages processed by the actor.
     */
    data class Stats(
        var createdAt: Instant = Clock.System.now(),
        var initializedAt: Instant? = null,
        var shutDownAt: Instant? = null,
        var lastMessageAt: Instant = Clock.System.now(),
        var processedMessages: Long = 0
    )

    private suspend inline fun <E> ReceiveChannel<E>.consumeEach(action: (E) -> Unit): Unit =
        consume {
            for (e in this) {
                try {
                    action(e)
                } catch (e: Exception) {
                    log.warn("[$address::consume] An error occurred while processing. ${e.message ?: ""}")
                }
            }

            @OptIn(DelicateCoroutinesApi::class)
            if (isClosedForReceive) {
                @OptIn(ExperimentalCoroutinesApi::class)
                if (!isEmpty) {
                    log.warn("[$address::consume] Channel is closed but there are still messages in the queue.")
                }
                status = Status.FINISHED
                ActorSystem.registry.unregister(this@Actor.address())
            }
        }

    private suspend fun replyActivationError(pattern: Patterns) {
        when (pattern) {
            is Patterns.Tell -> Unit
            is Patterns.Ask -> {
                val e = initializationFailed
                    ?: IllegalStateException("Actor is prematurely closed (could not be initialized).")
                val r: Result<Any> = Result.failure(e)
                reply(operation = "activate", pattern = pattern, reply = r)
            }
        }
    }
}
