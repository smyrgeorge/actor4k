package io.github.smyrgeorge.actor4k.actor.impl

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.extentions.launch
import kotlin.time.Duration

/**
 * Represents an actor that routes messages to a group of child actors using a defined routing strategy.
 *
 * The `RouterActor` class extends the `Actor` abstraction to provide functionality for managing
 * and routing messages to multiple child actors based on a specified `RoutingStrategy`. It supports
 * dynamic registration of child actors and ensures proper activation and shutdown of its dependencies.
 *
 * @param Req The type of message requests this actor handles, which must extend `Actor.Message`.
 * @param Res The type of message responses this actor produces, which must extend `Actor.Message.Response`.
 * @param key A unique identifier for the actor. Defaults to a randomly generated value prefixed with "router".
 * @param strategy The routing strategy used to distribute messages to child actors.
 * @param autoActivation Whether to automatically activate the actor and its child actors during initialization.
 * @param children Initial list of child actors to be registered with this router.
 */
abstract class RouterActor<Req : Actor.Message, Res : Actor.Message.Response>(
    key: String = randomKey("router"),
    val strategy: Strategy,
    autoActivation: Boolean = false,
    children: List<Child<Req, Res>> = emptyList()
) : Actor<Req, Res>(key) {

    private var id: Long = 0L
    private var children: Array<Child<Req, Res>> = children.toTypedArray()

    init {
        launch {
            if (autoActivation) activate()
            children.forEach { it.activate() }
        }
    }

    final override suspend fun onReceive(m: Req): Res {
        error("This method should never be called.")
    }

    /**
     * Sends a message to child actors according to the defined routing strategy.
     *
     * Based on the strategy, the message is sent to:
     * - A randomly selected child actor (`RANDOM`).
     * - A child actor in cyclic order (`ROUND_ROBIN`).
     * - All child actors simultaneously (`BROADCAST`).
     *
     * If no child actors are registered, the method returns a failure result.
     *
     * @param msg The message to be sent, inheriting from the [Message] class.
     * @return A [Result] wrapping [Unit] on success, or a failure if no children are registered.
     */
    final override suspend fun tell(msg: Message): Result<Unit> {
        if (children.isEmpty()) return Result.failure(IllegalStateException("No children registered."))

        id++
        when (strategy) {
            Strategy.RANDOM -> children.random().tell(msg)
            Strategy.ROUND_ROBIN -> children[id.toInt() % children.size].tell(msg)
            Strategy.BROADCAST -> children.forEach { it.tell(msg) }
        }

        return Result.success(Unit)
    }

    /**
     * Sends a message to one of the child actors and waits for a response within the specified timeout.
     *
     * This method uses the defined routing strategy to decide how the message is routed to child actors.
     * If the strategy is `RANDOM`, the message is sent to a randomly chosen child. If the strategy is
     * `ROUND_ROBIN`, the message is sent to child actors in a sequential, cyclic order. The `BROADCAST`
     * strategy is not supported with this method, and invoking it in this mode will result in a failure.
     *
     * If no child actors are registered, the method returns a failure result.
     *
     * @param msg The message to send to the child actor. It must conform to the expected message type.
     * @param timeout The maximum duration to wait for a response before timing out.
     * @return A [Result] containing the response of type [R] from the child actor, or a failure if an error occurs or no response is received within the timeout.
     */
    final override suspend fun <R : Res> ask(msg: Message, timeout: Duration): Result<R> {
        if (children.isEmpty()) return Result.failure(IllegalStateException("No children registered."))

        id++
        return when (strategy) {
            Strategy.RANDOM -> children.random().ask(msg, timeout)
            Strategy.ROUND_ROBIN -> children[id.toInt() % children.size].ask(msg, timeout)
            Strategy.BROADCAST -> Result.failure(IllegalStateException("Cannot use 'ask' with 'BROADCAST' strategy."))
        }
    }

    /**
     * Invoked during the shutdown process of the actor.
     *
     * This method iterates through all child actors and invokes their
     * respective `shutdown` method, ensuring that each child actor is
     * properly terminated. It is an essential part of the actor's lifecycle
     * to ensure clean and orderly shutdown of its dependencies.
     */
    final override suspend fun onShutdown() {
        children.forEach { it.shutdown() }
    }

    /**
     * Registers multiple actors to the current router actor and activates them immediately.
     *
     * @param actors Vararg of actor instances to be registered and activated.
     * @return This instance of RouterActor for method chaining.
     */
    fun register(vararg actors: Child<Req, Res>): RouterActor<Req, Res> {
        children = actors.toList().toTypedArray()
        children.forEach { launch { it.activate() } }
        return this
    }

    /**
     * Specifies the strategy used for routing messages to child actors in a `RouterActor`.
     *
     * Routing strategies define how the `RouterActor` distributes messages to its child actors:
     *
     * - `RANDOM`: A message is routed to a randomly chosen child actor.
     * - `BROADCAST`: The message is forwarded to all child actors simultaneously.
     * - `ROUND_ROBIN`: Messages are distributed in a sequential, cyclic order across child actors.
     */
    enum class Strategy {
        RANDOM, BROADCAST, ROUND_ROBIN
    }

    /**
     * Represents a protocol in the messaging or actor-based communication system.
     *
     * The `Protocol` class serves as an abstraction for defining specific protocol types that can be
     * used for communication between actors or components within the system. It extends the base
     * functionality of the `Message` class, inheriting properties like unique message identifiers
     * and creation timestamps.
     *
     * This class is typically used as a foundation for implementing domain-specific messaging
     * protocols by creating subclasses or specialized message types. Actors interacting with
     * protocols can leverage the provided structure for streamlined message processing and response
     * handling.
     *
     * ## Subclasses:
     * - `Protocol.Ok`: Represents a successful response, extending `Response`. This is commonly used
     *   as an acknowledgment or confirmation message in actor communication workflows.
     *
     * ## Usage context:
     * The `Protocol` class is designed to work within the context of actor-based programming and
     * ensures smooth message routing and lifecycle operations when used with actors like `RouterActor`.
     */
    abstract class Protocol : Message() {
        data object Ok : Response()
    }

    /**
     * Represents an abstract child actor within the actor-based system.
     *
     * The `Child` class is a specialized type of `Actor` designed to handle
     * requests of type `Req` and produce responses of type `Protocol.Ok`.
     * It provides a foundation for implementing child actor behavior in a
     * hierarchical actor model, where parent actors can create and manage
     * child actors.
     *
     * @param Req The type of message requests this actor can handle, which must
     * inherit from the `Message` class.
     */
    abstract class Child<Req : Message, Res : Message.Response>(
        capacity: Int = ActorSystem.conf.actorQueueSize,
    ) : Actor<Req, Res>(randomKey(), capacity)
}