package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.impl.BehaviorActor

class AccountBehaviourActor(key: String) :
    BehaviorActor<AccountBehaviourActor.Protocol, AccountBehaviourActor.Protocol.Response>(key) {

    // Define behavior functions
    private val echoBehavior: suspend (Protocol) -> Protocol.Response
    private val normalBehavior: suspend (Protocol) -> Protocol.Response

    init {
        // Initialize behaviors
        normalBehavior = { m ->
            log.info("[${address()}] Normal behavior received: $m")
            when (m) {
                is Protocol.Ping -> Protocol.Pong("Pong!")
                is Protocol.SwitchBehavior -> {
                    become(echoBehavior)
                    Protocol.BehaviorSwitched("Switched to echo behavior")
                }
            }
        }

        echoBehavior = { m ->
            log.info("[${address()}] Echo behavior received: $m")
            when (m) {
                is Protocol.Ping -> Protocol.Pong("Echo: ${m.message}")
                is Protocol.SwitchBehavior -> {
                    become(normalBehavior)
                    Protocol.BehaviorSwitched("Switched to normal behavior")
                }
            }
        }

        // Set initial behavior.
        become(normalBehavior)
    }

    override suspend fun onBeforeActivate() {
        // Optional override.
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        // Optional override.
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onShutdown() {
        // Optional override.
        log.info("[${address()}] onShutdown")
    }

    sealed interface Protocol : Actor.Protocol {
        sealed class Message<R : Actor.Protocol.Response> : Protocol, Actor.Protocol.Message<R>()
        sealed class Response : Actor.Protocol.Response()

        data class Ping(val message: String) : Message<Pong>()
        data class Pong(val message: String) : Response()
        data class SwitchBehavior(val behavior: String) : Message<BehaviorSwitched>()
        data class BehaviorSwitched(val message: String) : Response()
    }
}
