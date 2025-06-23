package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol
import kotlinx.serialization.Serializable

class AccountActor(key: String) : Actor<Protocol, Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        // Optional override.
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        // Optional override.
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        log.info("[${address()}] onReceive: $m")
        return when (m) {
            is Protocol.Ping -> Behavior.Respond(Protocol.Pong("Pong!"))
        }
    }

    override suspend fun onShutdown() {
        // Optional override.
        log.info("[${address()}] onShutdown")
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        @Serializable
        data class Ping(val message: String) : Message<Pong>()

        @Serializable
        data class Pong(val message: String) : Response()
    }
}