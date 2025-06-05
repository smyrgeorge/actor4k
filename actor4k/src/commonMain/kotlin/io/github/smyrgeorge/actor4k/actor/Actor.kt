package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
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
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents an actor in an actor-based concurrency model.
 *
 * An actor is a fundamental unit of computation that encapsulates state and behavior,
 * processes messages asynchronously, and communicates solely through message-passing.
 * This class implements various methods for managing the lifecycle, messaging, and
 * processing logic of an actor.
 *
 * @param key A unique identifier for the actor used for addressing and tracking.
 * @param capacity Indicates the actor's mailbox capacity (if mail is full, any attempt to send will suspend, back-pressure).
 */
abstract class Actor<Req : Actor.Message, Res : Actor.Message.Response>(
    val key: String,
    capacity: Int = ActorSystem.conf.actorQueueSize,
) {
    protected val log: Logger = ActorSystem.loggerFactory.getLogger(this::class)

    private val stats: Stats = Stats()
    private var status = Status.CREATED
    private var initializationFailed: Exception? = null
    private val address: Address = Address.of(this::class, key)
    private val ref: LocalRef = LocalRef(address = address, actor = this)
    private val mail: Channel<Patterns<Req, Res>> = Channel(capacity = capacity)

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
    open suspend fun onActivate(m: Req) {}

    /**
     * Handles the receipt of a message and processes it to produce a response.
     *
     * @param m The incoming request message of type [Req] that needs to be processed.
     * @return The response of type [Res] generated after processing the message.
     */
    abstract suspend fun onReceive(m: Req): Res

    /**
     * Hook invoked during the shutdown sequence of the actor.
     *
     * This method provides an opportunity to perform finalization tasks such as resource cleanup,
     * state persistence, or any other operations required before the actor is completely shut down.
     *
     * It is executed as part of the actor's shutdown process, after its status is set to `SHUTTING_DOWN`
     * and the mailbox is closed to prevent further message handling.
     *
     * Override this method in a subclass to implement custom shutdown logic. The function suspends
     * and allows for asynchronous operations to be performed during the shutdown phase.
     */
    open suspend fun onShutdown() {}

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
        if (status != Status.CREATED) return

        status = Status.ACTIVATING
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

                val msg = it.msg.apply { setId(stats.receivedMessages) }

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
                val reply: Result<Res> = runCatching { onReceive(msg).apply { setId(stats.receivedMessages) } }
                when (it) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> reply(operation = "consume", pattern = it, reply = reply)
                }
            }
        }
    }

    /**
     * Sends a message to the actor in a "fire-and-forget" manner.
     *
     * This function attempts to deliver the provided message to the actor,
     * ensuring it is in a state that can accept messages. If the actor cannot
     * accept messages, the function fails with an error. The message is sent
     * using the `Tell` communication pattern.
     *
     * @param msg The message to be sent, which must inherit from [Message].
     * @return A [Result] wrapping a successful operation as [Unit], or a failure
     * if the message could not be sent or the actor is unavailable.
     */
    open suspend fun tell(msg: Message): Result<Unit> = runCatching {
        if (!status.canAcceptMessages) error("$address is '$status' and thus is not accepting messages (try again later).")
        @Suppress("UNCHECKED_CAST") (msg as Req)
        val tell = Patterns.Tell<Req, Res>(msg)
        mail.send(tell)
    }

    /**
     * Sends a message to an actor and waits for a response within a specified timeout.
     *
     * This function validates that the actor can accept messages and sends the provided message
     * as part of an Ask communication pattern. The response is awaited within the specified timeout,
     * and the result is returned upon successful reception of the reply. If an error occurs or the
     * timeout is exceeded, the function returns a failure result.
     *
     * @param msg The message to be sent to the actor. It must be an instance of the expected message type.
     * @param timeout The duration to wait for a response before timing out. The default value is the
     * actor's configured timeout.
     * @return A [Result] containing the actor's response of type [R], or a failure if an exception occurs or the timeout expires.
     */
    open suspend fun <R : Res> ask(msg: Message, timeout: Duration = ActorSystem.conf.actorAskTimeout): Result<R> {
        val ask: Patterns.Ask<Req, Res> = runCatching {
            if (!status.canAcceptMessages) error("$address is '$status' and thus is not accepting messages (try again later).")
            @Suppress("UNCHECKED_CAST") (msg as Req)
            Patterns.Ask<Req, Res>(msg)
        }.getOrElse { return Result.failure(it) }

        @Suppress("UNCHECKED_CAST")
        return try {
            withTimeout(timeout) {
                mail.send(ask)
                ask.replyTo.receive()
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            ask.replyTo.close()
        } as Result<R>
    }

    /**
     * Sends a reply to an actor using a specified message pattern, handling timeouts and closed channels.
     *
     * This function attempts to send the response to the `replyTo` channel within a configurable timeout.
     * It also logs warnings in case of timeout, closed channel, or other exceptions.
     *
     * @param operation The operation name associated with the reply, used for logging.
     * @param pattern The `Ask` pattern containing the message payload and the channel for sending a response.
     * @param reply The result to be sent as a reply, encapsulated in a `Result` type.
     */
    private suspend fun reply(operation: String, pattern: Patterns.Ask<Req, Res>, reply: Result<Res>) {
        try {
            // We should be able to reply immediately.
            withTimeout(ActorSystem.conf.actorReplyTimeout) {
                pattern.replyTo.send(reply)
            }
        } catch (_: TimeoutCancellationException) {
            log.warn("[$address::$operation] Could not reply in time (timeout after ${ActorSystem.conf.actorReplyTimeout}) (the message was processed successfully).")
        } catch (_: ClosedSendChannelException) {
            log.warn("[$address::$operation] Could not reply, the channel is closed (the message was processed successfully).")
        } catch (e: Exception) {
            val error = e.message ?: "Unknown error."
            log.warn("[$address::$operation] Could not reply (the message was processed successfully). {}", error)
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
     * Represents an abstract message that can be used as a base class for custom message implementations.
     *
     * The `Message` class provides functionality to manage an identifier (`id`) and track the creation
     * timestamp of the message. It includes utility methods for determining message-specific states, such
     * as whether it is the first message.
     */
    @Serializable
    abstract class Message {
        var id: Long = -1L

        @Suppress("unused")
        val createdAt: Instant = Clock.System.now()

        /**
         * Determines if the current message is the first one.
         *
         * @return `true` if the message's identifier (`id`) is `1L`, indicating it is the first message; `false` otherwise.
         */
        fun isFirst(): Boolean = id == 1L

        /**
         * Sets the unique identifier for the message.
         *
         * @param id the identifier to be assigned to the message. This value will be stored and can be retrieved using the `id` property.
         */
        internal fun setId(id: Long) {
            this.id = id
        }

        /**
         * Represents an abstract base class for response messages.
         *
         * Response acts as a foundation for defining custom response types in the messaging or actor-based
         * communication system. It inherits from `Message.Response` and allows the implementation of
         * domain-specific responses that can be utilized in various messaging scenarios.
         *
         * Subclasses are expected to define their own structure and functionality, extending the core
         * capabilities provided by this abstract class.
         */
        @Serializable
        abstract class Response : Message()
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
    private sealed interface Patterns<Req : Message, Res : Message.Response> {
        val msg: Req

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
        class Tell<Req : Message, Res : Message.Response>(
            override val msg: Req
        ) : Patterns<Req, Res>

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
        class Ask<Req : Message, Res : Message.Response>(
            override val msg: Req,
            val replyTo: Channel<Result<Res>> = Channel(Channel.RENDEZVOUS)
        ) : Patterns<Req, Res>
    }

    /**
     * Represents the lifecycle status of an actor and determines its ability to accept messages.
     *
     * Each state in the `Status` enum carries a `canAcceptMessages` property that indicates whether
     * the actor can process incoming messages while in that specific state.
     *
     * The possible states of the actor are as follows:
     *
     * - `CREATED`: The actor is created but not yet activated. Messages can be accepted during this state.
     * - `ACTIVATING`: The actor is in the process of being activated. Messages can be accepted during this state.
     * - `READY`: The actor is fully initialized and ready to process messages. Messages can be accepted during this state.
     * - `SHUTTING_DOWN`: The actor is in the process of shutting down. Messages cannot be accepted during this state.
     * - `SHUT_DOWN`: The actor has completed the shutdown process. Messages cannot be accepted during this state.
     */
    @Serializable
    enum class Status(
        val canAcceptMessages: Boolean
    ) {
        CREATED(true),
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
    @Serializable
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

            try {
                // Use a timeout for the shutdown hook to prevent deadlocks
                withTimeout(ActorSystem.conf.actorShutdownHookTimeout) {
                    onShutdown()
                }
            } catch (_: TimeoutCancellationException) {
                log.error("[$address::onShutdown] Shutdown hook timed out after ${ActorSystem.conf.actorShutdownHookTimeout}. Forcing shutdown.")
            } catch (e: Exception) {
                log.error("[$address::onShutdown] Error during shutdown hook: ${e.message ?: "Unknown error"}")
            } finally {
                // Unregister the actor even if the shutdown hook fails or times out
                status = Status.SHUT_DOWN
                stats.shutDownAt = Clock.System.now()
                ActorSystem.registry.unregister(this@Actor.ref)
            }
        }

    private suspend fun replyActivationError(pattern: Patterns<Req, Res>) {
        when (pattern) {
            is Patterns.Tell -> Unit
            is Patterns.Ask -> {
                val e = initializationFailed
                    ?: IllegalStateException("Actor is prematurely closed (could not be initialized).")
                val r: Result<Res> = Result.failure(e)
                reply(operation = "activate", pattern = pattern, reply = r)
            }
        }
    }

    companion object {
        /**
         * Generates a unique random key as a string, which includes a "key-" prefix followed by a hash code
         * derived from a randomly generated UUID.
         *
         * @return A unique random key string in the format "key-{hashCode}".
         */
        fun randomKey(prefix: String = "key"): String {
            @OptIn(ExperimentalUuidApi::class)
            return "$prefix-${Uuid.random().hashCode().absoluteValue}"
        }
    }
}
