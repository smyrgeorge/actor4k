package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow

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
 * @param onBeforeActivate A suspendable lambda executed before activating the actor; defaults to an empty lambda.
 * @param onActivate A suspendable lambda executed when the actor is activated with an incoming request; defaults to an empty lambda.
 * @param afterReceive A suspendable lambda executed after each message is received and processed, taking the request and processing result as parameters; defaults to a no-op lambda.
 * @param onShutdown A suspendable lambda executed during the actor's shutdown process; defaults to an empty lambda.
 * @param onReceive A suspendable lambda defining the behavior of the actor when receiving a request.
 *                  It takes a `MutableStateFlow<State>` representing the actor's state and a request of type `Req`,
 *                  and it returns a `Behavior<Res>` instance representing the action to take.
 * @return A newly created `Actor` instance configured with the given parameters and behaviors.
 */
fun <State, Req : ActorProtocol, Res : ActorProtocol.Response> actorOf(
    initialState: State,
    key: String = Actor.randomKey(),
    capacity: Int = ActorSystem.conf.actorMailboxSize,
    stashCapacity: Int = ActorSystem.conf.actorStashSize,
    onMailboxBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    onBeforeActivate: suspend () -> Unit = {},
    onActivate: suspend (Req) -> Unit = {},
    afterReceive: suspend (m: Req, res: Result<Res>) -> Unit = { _, _ -> },
    onShutdown: suspend () -> Unit = {},
    onReceive: suspend (MutableStateFlow<State>, Req) -> Behavior<Res>,
): Actor<Req, Res> = object : Actor<Req, Res>(key, capacity, stashCapacity, onMailboxBufferOverflow) {
    private val state = MutableStateFlow(initialState)
    override suspend fun onBeforeActivate() = onBeforeActivate()
    override suspend fun onActivate(m: Req) = onActivate(m)
    override suspend fun onReceive(m: Req): Behavior<Res> = onReceive(state, m)
    override suspend fun afterReceive(m: Req, res: Result<Res>) = afterReceive(m, res)
    override suspend fun onShutdown() = onShutdown()
}.apply { activate() }
