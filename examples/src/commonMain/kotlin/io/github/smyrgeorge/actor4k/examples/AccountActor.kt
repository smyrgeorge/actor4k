package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol
import kotlinx.serialization.Serializable

class AccountActor(
    override val key: String
) : Actor<Protocol, Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        log.info("[${address()}] onReceive: $m")
        val res = when (m) {
            is Protocol.Req -> Protocol.Req.Resp("Pong!")
        }
        return res
    }

    sealed class Protocol : Message() {
        sealed class Response : Message.Response()

        @Serializable
        data class Req(val message: String) : Protocol() {
            @Serializable
            data class Resp(val message: String) : Response()
        }
    }
}