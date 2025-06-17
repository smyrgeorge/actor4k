package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.impl.BehaviorActor
import io.github.smyrgeorge.actor4k.examples.AccountBehaviourActor.Protocol

class AccountBehaviourActor(key: String) : BehaviorActor<Protocol, Protocol.Response>(key) {

    private val normalBehavior: suspend (Protocol) -> Protocol.Response = { m ->
        when (m) {
            is Protocol.Ping -> Protocol.Pong("Pong!")
            is Protocol.SwitchBehavior -> {
                become(echoBehavior)
                Protocol.BehaviorSwitched("Switched to echo behavior")
            }
        }
    }

    private val echoBehavior: suspend (Protocol) -> Protocol.Response = { m ->
        when (m) {
            is Protocol.Ping -> Protocol.Pong("Echo: ${m.message}")
            is Protocol.SwitchBehavior -> {
                become(normalBehavior)
                Protocol.BehaviorSwitched("Switched to normal behavior")
            }
        }
    }

    init {
        // Set initial behavior.
        become(normalBehavior)
    }

    override suspend fun onActivate(m: Protocol) {
        // Optional override.
        log.info("[${address()}] onActivate: $m")
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Ping(val message: String) : Message<Pong>()
        data class Pong(val message: String) : Response()
        data class SwitchBehavior(val behavior: String) : Message<BehaviorSwitched>()
        data class BehaviorSwitched(val message: String) : Response()
    }
}
