package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import kotlinx.serialization.Serializable

class AccountActor(key: String) : Actor<AccountActor.Protocol, AccountActor.Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        // Optional override.
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        // Optional override.
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        log.info("[${address()}] onReceive: $m")
        return when (m) {
            is Protocol.Ping -> Protocol.Pong("Pong!")
        }
    }

    override suspend fun onShutdown() {
        // Optional override.
        log.info("[${address()}] onShutdown")
    }

    sealed interface Protocol : Actor.Protocol {
        sealed class Message<R : Actor.Protocol.Response> : Protocol, Actor.Protocol.Message<R>()
        sealed class Response : Actor.Protocol.Response()

        @Serializable
        data class Ping(val message: String) : Message<Pong>()

        @Serializable
        data class Pong(val message: String) : Response()
    }
}