package io.github.smyrgeorge.actor4k.actor.impl

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration

/**
 * Represents a router actor that delegates messages to a collection of workers based on a specified routing strategy.
 *
 * The `RouterActor` manages a set of `Worker` instances and distributes messages to them using
 * predefined routing strategies, enabling efficient message handling in an actor-based system.
 *
 * @param Req the type of request messages this router actor can process. Must extend [ActorProtocol].
 * @param Res the type of response messages expected from the workers. Must extend [ActorProtocol.Response].
 * @param key an optional unique key to identify the router actor. Defaults to a random key prefixed with "router".
 * @param strategy the routing strategy used to delegate messages to workers.
 */
abstract class RouterActor<Req, Res>(
    key: String = randomKey("router"),
    val strategy: Strategy
) : Actor<Req, Res>(key) where Req : ActorProtocol, Res : ActorProtocol.Response {

    private var workers: Array<Worker<Req, Res>> = emptyArray()

    @OptIn(ExperimentalAtomicApi::class)
    private var workerId: AtomicInt = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    private fun workerId(): Int = workerId.incrementAndFetch() % workers.size

    // Do not limit the available queue (avoid deadlocks).
    private val available: Channel<Worker<Req, Res>> = Channel(Channel.UNLIMITED)

    final override suspend fun onReceive(m: Req): Behavior<Res> {
        error("This method should never be called.")
    }

    /**
     * Routes a message to the workers based on the configured strategy.
     *
     * The method distributes the provided message to the workers according to the routing strategy:
     * - `RANDOM`: Chooses a random worker to deliver the message.
     * - `ROUND_ROBIN`: Sends the message to workers in a circular order.
     * - `BROADCAST`: Delivers the message to all workers.
     * - `FIRST_AVAILABLE`: Routes the message to the first available worker.
     *
     * If no workers are registered, the method returns a failure result.
     *
     * @param msg The message to be sent, which must inherit from [ActorProtocol].
     * @return A [Result] wrapping [Unit] on successful dispatch or a failure if no workers are registered.
     */
    final override suspend fun tell(msg: ActorProtocol): Result<Unit> {
        if (workers.isEmpty()) return Result.failure(IllegalStateException("No workers are registered."))

        return when (strategy) {
            Strategy.RANDOM -> workers.random().tell(msg)
            Strategy.ROUND_ROBIN -> workers[workerId()].tell(msg)
            Strategy.BROADCAST -> {
                workers.forEach { it.tell(msg) }
                Result.success(Unit)
            }

            Strategy.FIRST_AVAILABLE -> {
                val worker = runCatching { available.receive() }.getOrElse { return Result.failure(it) }
                worker.tell(msg)
            }
        }
    }

    /**
     * Sends a message to a worker and awaits a response within a specified timeout duration.
     *
     * This method routes the provided message to one of the workers based on the configured routing strategy:
     * - `RANDOM`: Selects a worker randomly to process the message.
     * - `ROUND_ROBIN`: Cycles through workers sequentially to distribute the messages evenly.
     * - `BROADCAST`: Not supported with the `ask` method and will result in a failure.
     * - `FIRST_AVAILABLE`: Selects the first worker that is available to process the message.
     *
     * If no workers are registered, the method immediately returns a failure result.
     *
     * @param msg The message to be sent, which should extend [ActorProtocol.Message] and define a response type [R].
     * @param timeout The maximum duration to wait for a response before timing out.
     * @return A [Result] encapsulating the response of type [R] if successful, or an exception if the operation fails.
     */
    final override suspend fun <R, M> ask(msg: M, timeout: Duration): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response {
        if (workers.isEmpty()) return Result.failure(IllegalStateException("No workers are registered."))

        return when (strategy) {
            Strategy.RANDOM -> workers.random().ask(msg, timeout)
            Strategy.ROUND_ROBIN -> workers[workerId()].ask(msg, timeout)
            Strategy.BROADCAST -> Result.failure(IllegalStateException("Cannot use 'ask' with 'BROADCAST' strategy."))
            Strategy.FIRST_AVAILABLE -> {
                val worker = runCatching { available.receive() }.getOrElse { return Result.failure(it) }
                worker.ask(msg, timeout)
            }
        }
    }

    /**
     * Shuts down the router and its associated workers.
     *
     * This method performs a graceful shutdown by iterating through the registered
     * workers and invoking their individual `shutdown` methods.
     */
    final override suspend fun onShutdown() {
        workers.forEach { it.shutdown() }
    }

    /**
     * Registers the provided workers with the `RouterActor` for message routing and initializes them.
     *
     * The method ensures that all workers are activated and, in the case of the `FIRST_AVAILABLE` strategy,
     * registers their availability via a channel. This method can only be called before the router is initialized.
     * Once initialized, additional workers cannot be registered.
     *
     * @param actors The workers to be registered. Each worker is of type `Worker<Req, Res>`, where `Req` represents
     *               the request type and `Res` represents the response type.
     * @return The current instance of `RouterActor<Req, Res>` to allow method chaining.
     * @throws IllegalStateException If the method is called after the router is already initialized.
     */
    fun register(vararg actors: Worker<Req, Res>): RouterActor<Req, Res> {
        if (workers.isNotEmpty()) error("Cannot register new actors. Register function should only be called once.")

        workers = actors.toList().toTypedArray()

        workers.onEach { worker ->
            worker.activate()
            if (strategy == Strategy.FIRST_AVAILABLE) {
                worker.registerBecomeAvailableChannel(available)
                available.trySend(worker).onFailure { e ->
                    val error = e ?: IllegalStateException("Unknown error")
                    log.error("Failed to register worker.", error)
                    shutdown()
                    throw error
                }
            }
        }

        return this
    }

    /**
     * Represents the strategy used by the RouterActor to route messages to its workers.
     *
     * Each strategy defines a specific approach for how the RouterActor interacts with its
     * registered workers when sending or receiving messages.
     *
     * RANDOM:
     *   Selects a worker randomly for processing a request.
     *
     * BROADCAST:
     *   Broadcasts the message to all registered workers. Not supported for operations
     *   requiring a single response, like `ask`.
     *
     * ROUND_ROBIN:
     *   Routes messages to workers cyclically. After the last worker is used, it
     *   starts again from the first worker.
     *
     * FIRST_AVAILABLE:
     *   Assigns the task to the first worker that becomes available. This strategy requires
     *   workers to register their availability with the RouterActor.
     */
    enum class Strategy {
        RANDOM, BROADCAST, ROUND_ROBIN, FIRST_AVAILABLE;
    }

    /**
     * Represents an abstract worker in an actor-based system. The `Worker` class is designed to handle
     * specific types of requests and generate appropriate responses. It extends the `Actor` class and
     * provides additional functionality related to worker availability and task processing.
     *
     * @param Req The type of request the worker processes, which must inherit from [ActorProtocol].
     * @param Res The type of response the worker produces, which must be a subtype of [ActorProtocol.Response].
     * @param key The unique identifier for the worker, generated by default using a random key if not provided.
     * @param capacity The maximum size of the worker's internal message queue.
     * @param stashCapacity Indicates the actor's stash capacity.
     * @param onMailboxBufferOverflow Indicates the actor's behavior in case that the mailbox is full.
     */
    abstract class Worker<Req, Res>(
        key: String = randomKey("router-worker"),
        capacity: Int = ActorSystem.conf.actorMailboxSize,
        stashCapacity: Int = ActorSystem.conf.actorStashSize,
        onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND // Back-pressure.
    ) : Actor<Req, Res>(
        key = key,
        capacity = capacity,
        stashCapacity = stashCapacity,
        onMailboxBufferOverflow = onMailboxBufferOverflow
    ) where Req : ActorProtocol, Res : ActorProtocol.Response {
        private var signal: Channel<Worker<Req, Res>>? = null

        /**
         * Registers a `Channel` to signal the availability of the worker.
         * The provided channel is used to notify the system when the worker becomes available
         * to process new requests.
         *
         * @param ch the channel used to signal the availability of this worker.
         */
        internal fun registerBecomeAvailableChannel(ch: Channel<Worker<Req, Res>>) {
            signal = ch
        }

        final override suspend fun onBeforeActivate() {
            // Do not allow override this hook.
            // Ensure that the worker will be activated without errors.
        }

        final override suspend fun onActivate(m: Req) {
            // Do not allow override this hook.
            // Ensure that the worker will be activated without errors.
        }

        /**
         * Invoked after processing a request and generating a response. This method signals the availability of the worker
         * for further tasks by sending it to the associated availability channel if it is registered.
         *
         * @param m the request message that was processed by the worker.
         * @param res the result of processing the request, containing either a successful response or an error.
         */
        final override suspend fun afterReceive(m: Req, res: Result<Res>) {
            signal?.becomeAvailable(this)
        }

        /**
         * Signals the availability of the worker after processing a request.
         *
         * This method is called after receiving and processing a request. It notifies
         * the system that the worker is available for further tasks by sending itself
         * through the availability channel if one is registered.
         *
         * @param m the request message that was processed by the worker.
         */
        final override suspend fun afterReceive(m: Req) {
            signal?.becomeAvailable(this)
        }

        private fun Channel<Worker<Req, Res>>.becomeAvailable(worker: Worker<Req, Res>) {
            trySend(worker).onFailure { e ->
                val error = e ?: IllegalStateException("Unknown error")
                log.error("Failed to signal worker availability (worker lost from the pool).", error)
            }
        }
    }
}
