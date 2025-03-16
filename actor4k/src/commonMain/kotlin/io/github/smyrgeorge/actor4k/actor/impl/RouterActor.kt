package io.github.smyrgeorge.actor4k.actor.impl

import io.github.smyrgeorge.actor4k.actor.Actor
import kotlinx.coroutines.runBlocking

/**
 * Represents an actor capable of routing messages to a group of child actors.
 *
 * The `RouterActor` manages child actors, forwards received messages to them, and ensures
 * their proper shutdown during its lifecycle. This class serves as an abstraction layer
 * to distribute messages among multiple actors, enhancing scalability and modularity.
 */
class RouterActor(
    override val key: String = randomKey("router")
) : Actor<RouterActor.Protocol, RouterActor.Protocol.Ok>(key) {

    /**
     * Maintains a collection of child actors managed by the `RouterActor`.
     *
     * Child actors are instances of `Actor<out Protocol, Protocol.Ok>` and participate in the
     * message processing workflow. These actors are initialized, managed, and can be dynamically
     * registered or terminated through the lifecycle methods of the `RouterActor`.
     *
     * This array is initially empty and gets populated when child actors are registered
     * with the `register` method. The `onReceive` method forwards messages to
     * these child actors sequentially, and the `onShutdown` method ensures orderly
     * termination of all actors in this collection when the `RouterActor` is shut down.
     */
    private var childs: Array<Actor<out Protocol, Protocol.Ok>> = emptyArray()

    /**
     * Handles the reception of a message by forwarding it to all child actors sequentially.
     *
     * @param m The message of type [Protocol] to be forwarded to all child actors.
     * @return A response of type [Protocol.Ok] indicating that the message has been successfully processed and forwarded.
     */
    override suspend fun onReceive(m: Protocol): Protocol.Ok {
        for (child in childs) child.tell(m)
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
    override suspend fun onShutdown() {
        childs.forEach { it.shutdown() }
    }

    /**
     * Registers one or more child actors into the current `RouterActor` instance.
     *
     * This method stores the provided child actors and activates each of them before adding them
     * to the internal structure of the `RouterActor`. The activated actors will be managed by
     * this `RouterActor` and can participate in message processing.
     *
     * @param actors Variable number of actors to be registered. Each actor conforms to
     * `Actor<out Protocol, Protocol.Ok>`. These actors will become the child actors of
     * this `RouterActor`.
     * @return The current `RouterActor` instance with the newly registered child actors.
     */
    fun register(vararg actors: Actor<out Protocol, Protocol.Ok>): RouterActor {
        childs = actors.toList().toTypedArray()
        runBlocking { childs.forEach { it.activate() } }
        return this
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
}