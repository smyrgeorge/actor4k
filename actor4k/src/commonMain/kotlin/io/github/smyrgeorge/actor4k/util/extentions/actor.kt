package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.channels.BufferOverflow

/**
 * A container class representing a mutable state.
 *
 * This class is designed to hold a single mutable value of type `T`. It provides a mechanism
 * to encapsulate and manage mutable state, which is especially useful in concurrent or
 * stateful programming scenarios.
 *
 * @param T The type of the value being encapsulated.
 * @property value The current state of type `T`, which can be read and modified directly.
 */
class MutableState<T>(var value: T)

/**
 * Creates an actor instance configured with the specified state, key, capacity, and handlers for various lifecycle events.
 *
 * @param State The type of the actor's mutable state.
 * @param Req The type of requests the actor processes, which must extend `ActorProtocol`.
 * @param Res The type of responses the actor produces, which must extend `ActorProtocol.Response`.
 * @param initialState The initial state of the actor, represented as a value of type `State`.
 * @param key A unique key identifying the actor; defaults to a randomly generated key.
 * @param capacity The size of the actor's mailbox buffer; defaults to the value from `ActorSystem.conf.actorMailboxSize`.
 * @param stashCapacity The size of the actor's stash buffer; defaults to the value from `ActorSystem.conf.actorStashSize`.
 * @param onMailboxBufferOverflow The behavior for handling mailbox buffer overflows; defaults to `BufferOverflow.SUSPEND`.
 * @param onActivate A suspendable lambda executed when the actor is activated with an incoming request; defaults to an empty lambda.
 * @param onShutdown A suspendable lambda executed during the actor's shutdown process; defaults to an empty lambda.
 * @param onReceive A suspendable lambda defining the behavior of the actor when receiving a request.
 *                  It takes a `MutableState<State>` representing the actor's state and a request of type `Req`,
 *                  and it returns a `Behavior<Res>` instance representing the action to take.
 * @return A newly created `Actor` instance configured with the given parameters and behaviors.
 */
fun <State, Req : ActorProtocol, Res : ActorProtocol.Response> actorOf(
    initialState: State,
    key: String = Actor.randomKey(),
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
    onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    // Lifecycle handlers
    onActivate: suspend (MutableState<State>, Req) -> Unit = { _, _ -> },
    onShutdown: suspend () -> Unit = {},
    onReceive: suspend (MutableState<State>, Req) -> Behavior<Res>,
): Actor<Req, Res> = object : Actor<Req, Res>(key, capacity, stashCapacity, onMailboxBufferOverflow) {
    private val state = MutableState(initialState)
    override suspend fun onActivate(m: Req) = onActivate(state, m)
    override suspend fun onReceive(m: Req): Behavior<Res> = onReceive(state, m)
    override suspend fun onShutdown() = onShutdown()
}.apply { activate() }

/**
 * Represents a simple actor message within the actor protocol.
 *
 * This abstract class extends the ActorProtocol.Message class, inheriting its properties
 * and behavior while defining a response type parameter. It is designed to serve as a base
 * class for creating specific messages to be exchanged in the communication protocol.
 *
 * @param R The type of the response associated with this message, extending ActorProtocol.Response.
 */
abstract class SimpleActorMessage<R : ActorProtocol.Response> : ActorProtocol, ActorProtocol.Message<R>()

/**
 * Represents a simple response within the actor communication protocol.
 *
 * @param T The type of the value encapsulated by this response.
 * @property value The value contained in this response, typically representing the updated state or result of an operation.
 */
open class SimpleActorResponse<T>(open val value: T) : ActorProtocol.Response()

/**
 * Creates a simple actor with specified configuration parameters, state management, and lifecycle handlers.
 *
 * @param State The type of the actor's state.
 * @param initialState The initial state of the actor.
 * @param key A unique string representing the actor's key. Defaults to a randomly generated unique key.
 * @param capacity The maximum number of messages that can be held in the actor's mailbox. Defaults to the configured actor mailbox size.
 * @param stashCapacity The maximum number of messages that can be stashed. Defaults to the configured actor stash size.
 * @param onMailboxBufferOverflow The behavior when the mailbox buffer overflows. Defaults to suspending.
 * @param onActivate A lifecycle handler that is invoked when the actor is activated. It receives the mutable state and the activation message.
 * @param onShutdown A lifecycle handler that is invoked when the actor is shutting down. Defaults to an empty handler.
 * @param onReceive A handler invoked whenever the actor receives a message. It takes the current state and the received message and returns the updated state.
 * @return The created actor, which can process messages of type `SimpleActorMessage<SimpleActorResponse<State>>` and respond with `SimpleActorResponse<State>`.
 */
fun <State> simpleActorOf(
    initialState: State,
    key: String = Actor.randomKey(),
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
    onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    // Lifecycle handlers
    onActivate: suspend (MutableState<State>, SimpleActorMessage<SimpleActorResponse<State>>) -> Unit = { _, _ -> },
    onShutdown: suspend () -> Unit = {},
    onReceive: suspend (State, SimpleActorMessage<SimpleActorResponse<State>>) -> State,
): Actor<SimpleActorMessage<SimpleActorResponse<State>>, SimpleActorResponse<State>> = actorOf(
    initialState = initialState,
    key = key,
    capacity = capacity,
    stashCapacity = stashCapacity,
    onMailboxBufferOverflow = onMailboxBufferOverflow,
    onActivate = onActivate,
    onShutdown = onShutdown
) { state, message ->
    val updated = onReceive(state.value, message)
    state.value = updated
    Behavior.Reply(SimpleActorResponse(updated))
}
