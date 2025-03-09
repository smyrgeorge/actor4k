package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract class representing an actor with basic functionality for message handling,
 * activation, and status management.
 *
 * @property key the key identifying the actor.
 */
abstract class Actor<M : Actor.Message>(
    open val key: String
) {
    protected val log: Logger = ActorSystem.loggerFactory.getLogger(this::class)

    private val stats: Stats = Stats()
    private var status = Status.ACTIVATING
    private var initializationFailed: Exception? = null
    private val address: Address by lazy { Address.of(this::class, key) }
    private val ref: LocalRef by lazy { LocalRef(address = address, actor = this) }
    private val mail: Channel<Patterns<M>> = Channel(capacity = ActorSystem.conf.actorQueueSize)

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
    open suspend fun onActivate(m: M) {}

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
    abstract suspend fun onReceive(m: M, r: Response.Builder): Response

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
                stats.lastMessageAt = Clock.System.now()
                stats.receivedMessages += 1

                // Case that activation flow failed and we still have messages to consume.
                // If we get a shutdown event and the actor never initialized successfully,
                // we need to reply with an error and to drop all the messages.
                if (initializationFailed != null) {
                    replyActivationError(it)
                    return@consumeEach
                }

                val msg = it.msg.apply { id = stats.receivedMessages }

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
     * Sends a message to the actor in a fire-and-forget communication manner.
     *
     * This function ensures that the actor is in a state to accept messages before attempting to send.
     * It validates the message's type to ensure it matches the expected type for the actor.
     * If the actor cannot accept messages or the message type is incorrect, an error is thrown.
     * Upon successful validation, the message is encapsulated in a `Tell` pattern
     * and dispatched via the actor's mailbox.
     *
     * @param msg The message to be sent to the actor. It must be an instance of the expected message type.
     * @throws IllegalStateException If the actor is unable to accept messages due to its current status.
     * @throws ClassCastException If the message cannot be cast to the expected type.
     */
    suspend fun tell(msg: Any) {
        if (!status.canAcceptMessages) error("$address is '$status' and thus is not accepting messages (try again later).")
        @Suppress("UNCHECKED_CAST")
        msg as? M ?: throw ClassCastException("Could not cast to the expected type, m = $msg.")
        val tell = Patterns.Tell(msg)
        mail.send(tell)
    }

    /**
     * Sends a message to the actor and obtains a response synchronously within a specified timeout.
     *
     * The `ask` function enables a requester to send a message to an actor and wait for a response.
     * It ensures that the actor is in a state to accept messages before proceeding. If the actor
     * is unable to process the message (due to its current status or an invalid message type), an
     * exception is thrown.
     *
     * The message is sent using the `Ask` pattern, allowing communication in a request-response
     * manner. The function suspends until a response is received or the timeout duration elapses.
     *
     * @param msg The message to send to the actor. It must match the expected message type.
     * @param timeout The duration within which the actor must respond. Defaults to the actor system's
     *                configuration for ask timeout.
     * @return The response from the actor, cast to the expected type `R`.
     * @throws IllegalStateException If the actor cannot accept messages due to its current status.
     * @throws ClassCastException If the message or response cannot be cast to the expected types.
     */
    suspend fun <R> ask(msg: Any, timeout: Duration = ActorSystem.conf.actorAskTimeout): R {
        if (!status.canAcceptMessages) error("$address is '$status' and thus is not accepting messages (try again later).")
        @Suppress("UNCHECKED_CAST")
        msg as? M ?: throw ClassCastException("Could not cast to the expected type, m = $msg.")
        val ask = Patterns.Ask(msg)
        return try {
            withTimeout(timeout) {
                mail.send(ask)
                val reply = ask.replyTo.receive().getOrThrow()
                @Suppress("UNCHECKED_CAST")
                reply as? R ?: throw ClassCastException("Could not cast to the requested type.")
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
    private suspend fun reply(operation: String, pattern: Patterns.Ask<*>, reply: Result<Any>) {
        try {
            // We should be able to reply immediately.
            withTimeout(2.seconds) {
                pattern.replyTo.send(reply)
            }
        } catch (_: TimeoutCancellationException) {
            log.warn("[$address::$operation] Could not reply in time.")
        } catch (_: ClosedSendChannelException) {
            log.warn("[$address::$operation] Could not reply, the channel is closed.")
        } catch (e: Exception) {
            log.warn("[$address::$operation] Could not reply. {}", e.message ?: "Unknown error.")
        }
    }

    /**
     * Retrieves the current status of the actor.
     *
     * The status represents the actor's lifecycle state, which determines
     * whether it can accept messages or is in the process of shutting down.
     *
     * @return The current lifecycle status of the actor as a [Status].
     */
    fun status(): Status = status

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
     * Performs the shutdown process for the actor.
     *
     * This method updates the actor's lifecycle status to `SHUTTING_DOWN`
     * and records the shutdown time in the actor's statistics. It also
     * closes the actor's mailbox to prevent further message handling.
     *
     * This method is used to cleanly transition the actor out of its
     * active state and ensure proper resource management.
     */
    fun shutdown() {
        stats.triggeredShutDownAt = Clock.System.now()
        status = Status.SHUTTING_DOWN
        mail.close()
    }

    /**
     * Returns a reference to the current actor as a [LocalRef].
     *
     * The returned [LocalRef] provides details about the actor, including its
     * name, key, and class type, and enables interaction with the actor such
     * as sending messages and querying its status.
     *
     * @return a [LocalRef] representing the reference to the current actor.
     */
    fun ref(): LocalRef = ref

    /**
     * Represents a base structure for a message that can be exchanged within the system.
     *
     * This abstract class provides foundational elements for constructing and managing messages,
     * including an identifier and creation timestamp. Subclasses may implement additional
     * properties and behaviors specific to their use cases.
     */
    abstract class Message {
        var id: Long = -1

        @Suppress("unused")
        val createdAt: Instant = Clock.System.now()

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
    class Response(
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
    private sealed interface Patterns<M : Message> {
        val msg: M

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
        class Tell<M : Message>(
            override val msg: M
        ) : Patterns<M>

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
        class Ask<M : Message>(
            override val msg: M,
            val replyTo: Channel<Result<Any>> = Channel(Channel.RENDEZVOUS)
        ) : Patterns<M>
    }

    /**
     * Represents the lifecycle status of an actor.
     *
     * This enum defines the various stages that an actor can be in during its lifecycle, along with
     * whether the actor can accept incoming messages in each stage. The status is primarily
     * used to determine the actor's current state and its ability to handle messages:
     *
     * - `ACTIVATING`: The actor is in the process of becoming ready and is preparing for operation.
     * - `READY`: The actor is fully activated and ready to process messages.
     * - `SHUTTING_DOWN`: The actor is in the process of shutting down and cannot accept new messages.
     * - `SHUT_DOWN`: The actor has completed the shutdown process and is no longer active.
     *
     * @property canAcceptMessages Indicates whether the actor can process incoming messages in this state.
     */
    enum class Status(
        val canAcceptMessages: Boolean
    ) {
        ACTIVATING(true),
        READY(true),
        SHUTTING_DOWN(false),
        SHUT_DOWN(false)
    }

    /**
     * Represents statistics related to the lifecycle and message handling of an actor.
     *
     * This data class contains various timestamps and counters that provide insights into
     * the operational state of an actor, such as when it was created, initialized, shutdown,
     * the last time a message was processed, and the total number of messages it has processed.
     *
     * @property createdAt The timestamp when the actor was created.
     * @property initializedAt The timestamp when the actor was initialized. Nullable.
     * @property triggeredShutDownAt The timestamp when the actor was triggered to shut down. Nullable.
     * @property shutDownAt The timestamp when the actor completed its shutdown process. Nullable.
     * @property lastMessageAt The timestamp when the last message was processed by the actor.
     * @property receivedMessages The total number of messages received and processed by the actor.
     */
    data class Stats(
        var createdAt: Instant = Clock.System.now(),
        var initializedAt: Instant? = null,
        var triggeredShutDownAt: Instant? = null,
        var shutDownAt: Instant? = null,
        var lastMessageAt: Instant = Clock.System.now(),
        var receivedMessages: Long = 0
    )

    /**
     * Consumes each element from the `ReceiveChannel` and processes it using the provided action.
     * This function ensures safe consumption by handling exceptions during processing,
     * logging any errors, and performing necessary cleanup operations after consumption.
     *
     * @param E the type of elements in the `ReceiveChannel`.
     * @param action the function to process each element received from the channel.
     * @return Unit the result of the operation.
     */
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
            if (!isClosedForReceive) {
                log.warn("[$address::consume] Channel is not closed for receive but the actor is shutting down.")
            }
            ActorSystem.registry.unregister(this@Actor.ref)
            status = Status.SHUT_DOWN
            stats.shutDownAt = Clock.System.now()
        }

    private suspend fun replyActivationError(pattern: Patterns<*>) {
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

    companion object {
        private object ActorScope : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = EmptyCoroutineContext
        }

        private fun launch(f: suspend () -> Unit) {
            ActorScope.launch(Dispatchers.Default) { f() }
        }
    }
}
