package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.actor.impl.BehaviorActor
import io.github.smyrgeorge.actor4k.examples.AccountBehaviourActor.Protocol

class AccountBehaviourActor(key: String) : BehaviorActor<Protocol, Protocol.Response>(key) {

    override suspend fun onActivate(m: Protocol) {
        // Set the default behavior here.
        become(normal)
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Ping(val message: String) : Message<Pong>()
        data class Pong(val message: String) : Response()
        data class SwitchBehavior(val behavior: String) : Message<BehaviorSwitched>()
        data class BehaviorSwitched(val message: String) : Response()
    }

    companion object {
        private val normal: suspend (AccountBehaviourActor, Protocol) -> Behavior<Protocol.Response> = { ctx, m ->
            ctx.log.info("[${ctx.address()}] normalBehavior: $m")

            when (m) {
                is Protocol.Ping -> Behavior.Respond(Protocol.Pong("Pong!"))
                is Protocol.SwitchBehavior -> {
                    ctx.become(echo)
                    Behavior.Respond(Protocol.BehaviorSwitched("Switched to echo behavior"))
                }
            }
        }

        private val echo: suspend (AccountBehaviourActor, Protocol) -> Behavior<Protocol.Response> = { ctx, m ->
            ctx.log.info("[${ctx.address()}] echoBehavior: $m")

            when (m) {
                is Protocol.Ping -> Behavior.Respond(Protocol.Pong("Echo: ${m.message}"))
                is Protocol.SwitchBehavior -> {
                    ctx.become(normal)
                    Behavior.Respond(Protocol.BehaviorSwitched("Switched to normal behavior"))
                }
            }
        }
    }
}
