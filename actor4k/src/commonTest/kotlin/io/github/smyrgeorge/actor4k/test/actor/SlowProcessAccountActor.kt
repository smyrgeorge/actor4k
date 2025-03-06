package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay

open class SlowProcessAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        delay(1000)
        val msg = m.cast<Req>()
        log.info("[${address()}] Received message: $msg")
        val res = Resp("Pong!")
        return r.value(res).build()
    }
}