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
