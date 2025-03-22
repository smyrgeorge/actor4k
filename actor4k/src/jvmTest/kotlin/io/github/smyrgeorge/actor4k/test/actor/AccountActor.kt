package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor
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
        val res = when (m) {
            is Protocol.Req -> Protocol.Req.Resp("Pong!")
        }
        return res
    }

    sealed class Protocol : Message() {
        sealed class Response : Message.Response()
        data class Req(val message: String) : Protocol() {
            data class Resp(val message: String) : Response()
        }
    }
}