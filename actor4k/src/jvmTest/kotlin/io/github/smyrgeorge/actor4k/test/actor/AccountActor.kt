package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol

open class AccountActor(key: String) : Actor<Protocol, Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        log.info("[${address()}] onReceive: $m")
        return when (m) {
            is Protocol.Req -> Protocol.Resp("Pong!")
        }
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()
        data class Req(val message: String) : Message<Resp>()
        data class Resp(val message: String) : Response()
    }
}