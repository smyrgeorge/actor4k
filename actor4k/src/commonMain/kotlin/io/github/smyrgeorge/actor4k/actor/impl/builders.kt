package io.github.smyrgeorge.actor4k.actor.impl

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
 * @param initial The initial state of the actor, represented as a value of type `State`.
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
    initial: State,
    key: String = Actor.randomKey(),
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
    onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    // Lifecycle handlers
    onActivate: suspend (Req) -> Unit = {},
    onShutdown: suspend () -> Unit = {},
    onReceive: suspend (MutableState<State>, Req) -> Behavior<Res>,
): Actor<Req, Res> = object : Actor<Req, Res>(key, capacity, stashCapacity, onMailboxBufferOverflow) {
    private val state = MutableState(initial)
    override suspend fun onActivate(m: Req) = onActivate(m)
    override suspend fun onReceive(m: Req): Behavior<Res> = onReceive(state, m)
    override suspend fun onShutdown() = onShutdown()
}.apply { activate() }

/**
 * Represents a simplified message within the actor communication protocol.
 *
 * The `SimpleMessage` class serves as an abstraction for protocol messages that expect a
 * `SimpleResponse` as a reply. It extends the `ActorProtocol.Message` class, inheriting
 * functionality common to all protocol messages and providing a specific type binding for
 * responses.
 *
 * @param R The type of response value expected in the associated `SimpleResponse`.
 */
abstract class SimpleMessage<R> : ActorProtocol, ActorProtocol.Message<SimpleResponse<R>>()

/**
 * A generic implementation of a response within the actor protocol.
 *
 * This class represents a concrete response type used for replies in the communication protocol,
 * wrapping a value of a specific type. It extends the ActorProtocol.Response class.
 *
 * @param T The type of the value contained within this response.
 * @property value The value representing the content of this response.
 */
class SimpleResponse<T>(val value: T) : ActorProtocol.Response()

/**
 * Creates a simple actor with specified configuration and behavior.
 *
 * This method constructs an actor that operates using a state-based model with custom lifecycle handlers.
 * The actor responds to `SimpleMessage<SimpleResponse<State>>` messages and provides a `SimpleResponse<State>`
 * upon processing.
 *
 * @param initial The initial state of the actor.
 * @param key A unique key for the actor. Default is a randomly generated key.
 * @param capacity The maximum number of messages the actor's mailbox can hold. Defaults to the system-configured mailbox size.
 * @param stashCapacity The maximum number of stashed messages the actor can hold. Defaults to the system-configured stash size.
 * @param onMailboxBufferOverflow Behavior of the actor when the mailbox overflows. Default is `BufferOverflow.SUSPEND`.
 * @param onActivate Lifecycle handler invoked when the actor is activated. Default is an empty lambda.
 * @param onShutdown Lifecycle handler invoked when the actor is shut down. Default is an empty lambda.
 * @param onReceive Handler invoked for processing incoming messages. Updates and returns the new state.
 * @return An actor that processes `SimpleMessage<SimpleResponse<State>>` messages and returns `SimpleResponse<State>` results.
 */
fun <State> simpleActorOf(
    initial: State,
    key: String = Actor.randomKey(),
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
    onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    // Lifecycle handlers
    onActivate: suspend (SimpleMessage<SimpleResponse<State>>) -> Unit = {},
    onShutdown: suspend () -> Unit = {},
    onReceive: suspend (State, SimpleMessage<SimpleResponse<State>>) -> State,
): Actor<SimpleMessage<SimpleResponse<State>>, SimpleResponse<State>> = actorOf(
    initial = initial,
    key = key,
    capacity = capacity,
    stashCapacity = stashCapacity,
    onMailboxBufferOverflow = onMailboxBufferOverflow,
    onActivate = onActivate,
    onShutdown = onShutdown
) { state, message ->
    val updated = onReceive(state.value, message)
    state.value = updated
    Behavior.Reply(SimpleResponse(updated))
}

/**
 * Creates a router-based actor system with a specified routing strategy and number of worker actors.
 *
 * @param Req The type of the protocol request, extending `ActorProtocol`.
 * @param Res The type of the protocol response, extending `ActorProtocol.Response`.
 * @param strategy The routing strategy that determines how requests are delegated to worker actors.
 * @param numberOfWorkers The number of worker actors to be created for processing requests.
 * @param onReceive A suspendable function that defines the behavior executed by each worker actor upon receiving a request.
 * @return A `RouterActor` instance configured with the specified strategy and worker actors.
 */
fun <Req : ActorProtocol, Res : ActorProtocol.Response> routerActorOf(
    strategy: RouterActor.Strategy,
    numberOfWorkers: Int,
    onReceive: suspend (Req) -> Behavior<Res>
): RouterActor<Req, Res> {
    val workers = (1..numberOfWorkers).map {
        object : RouterActor.Worker<Req, Res>() {
            override suspend fun onReceive(m: Req): Behavior<Res> = onReceive(m)
        }
    }.toTypedArray()

    return object : RouterActor<Req, Res>(strategy = strategy) {}.apply { register(*workers) }
}

/**
 * Creates an instance of a simple router actor with the specified routing strategy and workers.
 *
 * @param strategy The routing strategy to be used by the router actor.
 * @param numberOfWorkers The number of worker actors to manage and distribute the workload.
 * @param onReceive A suspendable function that processes incoming messages of type SimpleMessage and produces a result of type T.
 * @return A RouterActor that processes SimpleMessage and provides a SimpleResponse with the processed result.
 */
fun <T> simpleRouterActorOf(
    strategy: RouterActor.Strategy,
    numberOfWorkers: Int,
    onReceive: suspend (SimpleMessage<SimpleResponse<T>>) -> T
): RouterActor<SimpleMessage<SimpleResponse<T>>, SimpleResponse<T>> =
    routerActorOf(strategy, numberOfWorkers) { message ->
        val res = onReceive(message)
        Behavior.Reply(SimpleResponse(res))
    }
