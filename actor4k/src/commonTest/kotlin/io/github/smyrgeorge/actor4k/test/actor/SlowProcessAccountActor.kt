package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Req
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Resp
import kotlinx.coroutines.delay

open class SlowProcessAccountActor(override val key: String) : Actor(key) {
    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        delay(1000)
        val msg = m.cast<Req>()
        log.info("[${address()}] Received message: $msg")
        val res = Resp("Pong!")
        return r.value(res).build()
    }
}