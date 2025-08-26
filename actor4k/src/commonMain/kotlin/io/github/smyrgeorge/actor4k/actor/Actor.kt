@file:OptIn(ExperimentalTime::class)

package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.instantFromEpochMilliseconds
import io.github.smyrgeorge.actor4k.util.extentions.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
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
 * @param stashCapacity Indicates the actor's stash capacity.
 */
abstract class Actor<Req : ActorProtocol, Res : ActorProtocol.Response>(
    val key: String,
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
) {
    protected val log: Logger = ActorSystem.loggerFactory.getLogger(this::class)

    private val stats: Stats = Stats()
    private var status = Status.CREATED
    private var initializationFailed: Exception? = null
    private val address: Address = Address.of(this::class, key)
    private val ref: LocalRef = LocalRef(address = address, actor = this)

    private var current: Patterns<Req, Res>? = null
    private val mail: Channel<Patterns<Req, Res>> = Channel(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.SUSPEND // Back-pressure.
    )
    private val stash: Channel<Patterns<Req, Res>> = Channel(
        capacity = stashCapacity,
        onBufferOverflow = BufferOverflow.DROP_LATEST,
        onUndeliveredElement = { log.warn("[$address::stash] Stash is full, dropping message: $it") }
    )

    /**
     * Hook called before the actor is activated.
     *
     * This method provides a pre-activation step for setting up or preparing
     * the actor before it processes its first message.
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
     * begins processing further messages. It allows initializing or setting up
     * the actor's state based on the provided initial message.
     *
     * @param m The initial message used to activate and initialize the actor.
     */
    open suspend fun onActivate(m: Req) {}

    /**
     * Handles receiving a request and returns a corresponding response wrapped in a Behavior.
     *
     * @param m The incoming request of type Req.
     * @return The resulting response of type Res wrapped in a Behavior.
     */
    abstract suspend fun onReceive(m: Req): Behavior<Res>

    /**
     * Hook called after the actor has finished processing a message.
     *
     * This method provides an optional post-processing step for handling actions that should occur
     * after the actor completes processing a message. It is executed after the result of the processing
     * is generated, whether it is successful or a failure. Override this method in a subclass to
     * implement custom logic such as logging, additional state updates, or cleanup tasks.
     *
     * @param m The original request message of type [Req] that was processed.
     * @param res The result of the processing of type [Res], encapsulated in a [Result] object that
     * represents either a success or a failure.
     */
    internal open suspend fun afterReceive(m: Req, res: Result<Res>) {}

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
     * Activates the actor and transitions its state from `CREATED` to `ACTIVATING`, and eventually to `READY`
     * upon successful initialization.
     *
     * When activated, the method begins consuming messages from the mail queue. The activation process follows these steps:
     * - Executes pre-activation hooks using `onBeforeActivate`.
     * - Processes incoming messages iteratively from the mail queue.
     * - Handles the activation flow for the first received message and transitions to the `READY` state if successful.
     * - Handles message consumption, including success and error scenarios, by invoking the relevant hooks,
     *   such as `onReceive`, `afterReceive`, and others.
     *
     * If an error occurs during activation or message processing:
     * - Activation errors result in immediate shutdown of the actor.
     * - Message processing errors are handled by sending error responses or stashing messages, depending on their type.
     *
     * The method also updates the internal statistics such as the timestamp of the last received message
     * and the total count of received messages.
     *
     * Note:
     * - This method only starts the activation process if the actor's current status is `CREATED`.
     * - Subsequent messages are processed based on the defined behavior in message hooks like `onReceive`
     *   and its associated logic.
     */
    fun activate() {
        if (status != Status.CREATED) return

        // Start the mail consumer.
        launch {
            try {
                // Execute pre-activation hooks.
                onBeforeActivate()
            } catch (e: Exception) {
                // In case of an error, we need to close the [Actor] immediately.
                log.error("[$address::onBeforeActivate] Failed to activate, will shutdown (${e.message ?: ""})")
                initializationFailed = e
                shutdown()
            }

            status = Status.ACTIVATING

            mail.consumeEach { pattern ->
                stats.lastMessageAt = Clock.System.now().toEpochMilliseconds()
                stats.receivedMessages += 1

                // Case that activation flow failed and we still have messages to consume.
                // If we get a shutdown event and the actor never initialized successfully,
                // we need to reply with an error and to drop all the messages.
                if (initializationFailed != null) {
                    replyActivationError(pattern)
                    return@consumeEach
                }

                val msg: Req = pattern.msg.apply { id = stats.receivedMessages }

                // Activation flow.
                if (msg.isFirst()) {
                    try {
                        onActivate(msg)
                        // Set 'READY' status.
                        status = Status.READY
                        stats.initializedAt = Clock.System.now().toEpochMilliseconds()
                    } catch (e: Exception) {
                        // In case of an error, we need to close the [Actor] immediately.
                        log.error("[$address::activate] Failed to activate, will shutdown (${e.message ?: ""})")
                        initializationFailed = e
                        replyActivationError(pattern)
                        shutdown()
                        return@consumeEach
                    }
                }

                // Process the message.
                current = pattern
                process(pattern)
                current = null
            }
        }
    }


    /**
     * Processes a given pattern, handling the message, determining the behavior,
     * handling the response, and managing post-receive operations.
     *
     * @param pattern A pattern instance that contains the message to process and
     *                the type of pattern (e.g., ask or tell).
     */
    private suspend fun process(pattern: Patterns<Req, Res>) {
        val msg: Req = pattern.msg

        // Process the message.
        val behavior: Behavior<Res> = try {
            when (val r = onReceive(msg)) {
                is Behavior.Reply -> r.apply { value.id = pattern.msg.id } // Set the message ID to the response.
                else -> r
            }
        } catch (e: Exception) {
            Behavior.Error(e)
        }

        when (behavior) {
            is Behavior.Reply, is Behavior.Error -> {
                val result = when (behavior) {
                    is Behavior.Reply -> Result.success(behavior.value)
                    is Behavior.Error -> Result.failure(behavior.cause)
                    else -> Result.failure(Exception("Unexpected behavior: $behavior"))
                }

                // Send the response back to the sender.
                when (pattern) {
                    is Patterns.Tell -> Unit
                    is Patterns.Ask -> reply("consume", pattern, result)
                }

                // Handle afterReceive hook.
                try {
                    afterReceive(msg, result)
                } catch (e: Exception) {
                    log.warn("[$address::afterReceive] Failed to process afterReceive hook (${e.message ?: ""})")
                }
            }

            is Behavior.None -> Unit
            is Behavior.Shutdown -> shutdown()
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
     * @param msg The message to be sent, which must inherit from [ActorProtocol].
     * @return A [Result] wrapping a successful operation as [Unit], or a failure
     * if the message could not be sent or the actor is unavailable.
     */
    open suspend fun tell(msg: ActorProtocol): Result<Unit> = runCatching {
        if (!status.canAcceptMessages) error("$address is '$status' and thus is not accepting messages (try again later).")
        @Suppress("UNCHECKED_CAST") (msg as Req)
        val tell = Patterns.Tell<Req, Res>(msg)
        mail.send(tell)
    }

    /**
     * Sends a message to the actor and awaits a response within a specified timeout duration.
     * This method uses the `Ask` interaction pattern, where a request is sent, and the sender
     * waits for a reply from the recipient.
     *
     * @param msg The message to send, which should inherit from [ActorProtocol.Message] and have a corresponding response type [R].
     * @param timeout The maximum duration to wait for a response. If unspecified, defaults to the actor system's configured ask timeout.
     * @return A [Result] wrapping the response of type [R] if successful, or an exception in case of failure or timeout.
     */
    open suspend fun <R, M> ask(msg: M, timeout: Duration = ActorSystem.conf.actorAskTimeout): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response {
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
     * Stashes the current message for future processing.
     *
     * This method retrieves the current message and stores it in a stash container.
     * If no current message is available, it throws an error.
     *
     * @throws IllegalStateException if there is no current message to stash.
     */
    suspend fun stash() {
        val pattern = current ?: error("No current message to stash.")
        stash.send(pattern)
        stats.stashedMessages += 1
    }

    /**
     * Un-stashes all messages from the stash and processes them.
     *
     * This method drains the stash channel by continuously retrieving the stashed messages,
     * decrementing the stashed messages count, and forwarding the messages for processing.
     * It ensures that all messages in the stash are handled correctly.
     */
    protected suspend fun unstashAll() {
        // Drain the stash channel.
        @OptIn(ExperimentalCoroutinesApi::class)
        while (!stash.isEmpty) {
            val pattern = stash.receive()
            stats.stashedMessages -= 1
            process(pattern)
        }
    }

    /**
     * Performs the shutdown process for the actor.
     *
     * This method updates the actor's lifecycle status to `SHUTTING_DOWN`
     * and records the shutdown time in the actor's statistics. It also
     * closes the actor's mailbox and stash to prevent further message handling.
     *
     * This method is used to cleanly transition the actor out of its
     * active state and ensure proper resource management.
     */
    fun shutdown() {
        if (!status.canTriggerShutdown) return
        stats.triggeredShutDownAt = Clock.System.now().toEpochMilliseconds()
        status = Status.SHUTTING_DOWN
        mail.close()
        stash.close()
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
    private sealed interface Patterns<Req : ActorProtocol, Res : ActorProtocol.Response> {
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
        class Tell<Req : ActorProtocol, Res : ActorProtocol.Response>(
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
        class Ask<Req : ActorProtocol, Res : ActorProtocol.Response>(
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
        val canAcceptMessages: Boolean,
        val canTriggerShutdown: Boolean,
    ) {
        CREATED(true, true),
        ACTIVATING(true, true),
        READY(true, true),
        SHUTTING_DOWN(false, false),
        SHUT_DOWN(false, false)
    }

    /**
     * Represents statistics related to the lifecycle and message handling of an actor.
     *
     * This data class contains various timestamps and counters that provide insights into
     * the operational state of an actor, such as when it was created, initialized, shutdown.
     * The last time a message was processed, and the total number of messages it has processed.
     *
     * @property createdAt The timestamp when the actor was created.
     * @property initializedAt The timestamp when the actor was initialized. Nullable.
     * @property triggeredShutDownAt The timestamp when the actor was triggered to shut down. Nullable.
     * @property shutDownAt The timestamp when the actor completed its shutdown process. Nullable.
     * @property lastMessageAt The timestamp when the actor processed the last message.
     * @property receivedMessages The total number of messages received and processed by the actor.
     * @property stashedMessages The total number of messages that have been stashed by the actor.
     */
    @Serializable
    data class Stats(
        var createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        var initializedAt: Long? = null,
        var triggeredShutDownAt: Long? = null,
        var shutDownAt: Long? = null,
        var lastMessageAt: Long = Clock.System.now().toEpochMilliseconds(),
        var receivedMessages: Long = 0,
        var stashedMessages: Long = 0
    ) {
        override fun toString(): String = buildString {
            append("ActorStats(")
            append("createdAt=${instantFromEpochMilliseconds(createdAt)}, ")
            append("initializedAt=${instantFromEpochMilliseconds(initializedAt)}, ")
            append("triggeredShutDownAt=${instantFromEpochMilliseconds(triggeredShutDownAt)}, ")
            append("shutDownAt=${instantFromEpochMilliseconds(shutDownAt)}, ")
            append("lastMessageAt=${instantFromEpochMilliseconds(lastMessageAt)}, ")
            append("receivedMessages=${instantFromEpochMilliseconds(receivedMessages)}, ")
            append("stashedMessages=$stashedMessages")
            append(")")
        }
    }

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
                stats.shutDownAt = Clock.System.now().toEpochMilliseconds()
                ActorSystem.registry.unregister(this@Actor.ref)
            }
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
     * Handles the activation error reply logic based on the provided pattern.
     *
     * @param pattern The communication pattern used, which could either be of type `Tell` or `Ask`.
     *                If the pattern is `Ask`, an error result is sent back to the requester.
     */
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
            return "${prefix.removeSuffix("-")}-${Uuid.random().hashCode().absoluteValue}"
        }
    }
}
