package io.github.smyrgeorge.actor4k.actor.impl

import io.github.smyrgeorge.actor4k.actor.Actor

/**
 * Represents an actor that can change its behavior based on the current state or message received.
 *
 * The `BehaviorActor` allows for dynamic behavior changes during the actor's lifecycle,
 * enabling it to respond differently to the same message types depending on its current state.
 * This is useful for implementing state machines or actors that need to change their
 * processing logic based on previous interactions.
 *
 * @param Req the type of request messages this behavior actor can process. Must extend [Actor.Protocol].
 * @param Res the type of response messages this behavior actor can generate. Must extend [Actor.Protocol.Response].
 * @param key an optional unique key to identify the behavior actor. Defaults to a random key prefixed with "behavior".
 * @param behavior the initial behavior function that processes messages. Defaults to a function that throws an exception.
 */
abstract class BehaviorActor<Req : Actor.Protocol, Res : Actor.Protocol.Response>(
    key: String,
    private var behavior: suspend (Req) -> Res = { _ -> error("No behavior set.") }
) : Actor<Req, Res>(key) {

    /**
     * Processes the received message using the current behavior function.
     *
     * This method delegates the message processing to the current behavior function,
     * allowing the actor to respond differently based on its current state.
     *
     * @param m The incoming request message of type [Req] that needs to be processed.
     * @return The response of type [Res] generated after processing the message.
     */
    final override suspend fun onReceive(m: Req): Res = behavior(m)

    /**
     * Changes the actor's behavior to a new function.
     *
     * This method allows the actor to dynamically change how it processes messages
     * by replacing the current behavior function with a new one.
     *
     * @param newBehavior The new behavior function that will process future messages.
     */
    protected fun become(newBehavior: suspend (Req) -> Res) {
        behavior = newBehavior
    }
}
