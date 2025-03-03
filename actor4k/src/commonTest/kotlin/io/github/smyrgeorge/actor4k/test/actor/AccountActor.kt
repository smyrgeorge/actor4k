package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor

data class AccountActor(override val key: String) : Actor(key) {

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] before-activate")
    }

    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] activate ($m)")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Req>()
        log.info("[${address()}] Received message: $msg")
        val res = Resp("Pong!")
        return r.value(res).build()
    }

    data class Req(val msg: String)
    data class Resp(val msg: String)
}