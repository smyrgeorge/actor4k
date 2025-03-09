package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor

open class AccountActor(override val key: String) : Actor(key) {

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Protocol>()
        log.info("[${address()}] onReceive: $msg")
        val res = when (msg) {
            is Protocol.Req -> Protocol.Req.Resp("Pong!")
        }
        return r.value(res).build()
    }

    sealed class Protocol : Message() {
        data class Req(val message: String) : Protocol() {
            data class Resp(val message: String)
        }
    }
}