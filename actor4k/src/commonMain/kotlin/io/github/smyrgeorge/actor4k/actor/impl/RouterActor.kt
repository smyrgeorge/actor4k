package io.github.smyrgeorge.actor4k.actor.impl

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor.Protocol
import kotlinx.coroutines.runBlocking

/**
 * Represents an actor that routes messages to a group of child actors using a defined routing strategy.
 *
 * The `RouterActor` class extends the `Actor` abstraction to provide functionality for managing
 * and routing messages to multiple child actors based on a specified `RoutingStrategy`. It supports
 * dynamic registration of child actors and ensures proper activation and shutdown of its dependencies.
 *
 * @param Req The type of message requests this actor handles, which must extend `Actor.Message`.
 * @param key A unique identifier for the actor. Defaults to a randomly generated value prefixed with "router".
 * @param strategy The routing strategy used to distribute messages to child actors.
 * @param autoActivation Whether to automatically activate the actor and its child actors during initialization.
 * @param childs Initial list of child actors to be registered with this router.
 */
abstract class RouterActor<Req : Actor.Message>(
    override val key: String = randomKey("router"),
    val strategy: Strategy,
    autoActivation: Boolean = false,
    childs: List<Child<Req>> = emptyList()
) : Actor<Req, Protocol.Ok>(key) {

    private var childs: Array<Child<Req>> = childs.toTypedArray()

    init {
        runBlocking {
            if (autoActivation) activate()
            childs.forEach { it.activate() }
        }
    }

    /**
     * Processes the received message and forwards it to all child actors.
     *
     * This method iterates through the list of child actors and dispatches the given message
     * to each child using the `tell` function. After forwarding the message to all child actors,
     * it returns a successful acknowledgment response.
     *
     * @param m The message of type `Req` to be processed and forwarded to the child actors.
     * @return A `Protocol.Ok` object indicating the successful handling of the message.
     */
    final override suspend fun onReceive(m: Req): Protocol.Ok {
        if (childs.isEmpty()) return Protocol.Ok

        when (strategy) {
            Strategy.RANDOM -> childs.random().tell(m)
            Strategy.BROADCAST -> for (child in childs) child.tell(m)
            Strategy.ROUND_ROBIN -> childs[m.id.toInt() % childs.size].tell(m)
        }

        return Protocol.Ok
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
        childs.forEach { it.shutdown() }
    }

    /**
     * Registers multiple actors to the current router actor and activates them immediately.
     *
     * @param actors Vararg of actor instances to be registered and activated.
     * @return This instance of RouterActor for method chaining.
     */
    fun register(vararg actors: Child<Req>): RouterActor<Req> {
        childs = actors.toList().toTypedArray()
        runBlocking { childs.forEach { it.activate() } }
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
    abstract class Child<Req : Message> : Actor<Req, Protocol.Ok>(randomKey())
}