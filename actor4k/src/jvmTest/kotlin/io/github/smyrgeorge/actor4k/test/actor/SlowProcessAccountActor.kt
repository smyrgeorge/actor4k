package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay

open class SlowProcessAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Protocol.Response {
        delay(1000)
        log.info("[${address()}] Received message: $m")
        return Protocol.Req.Resp("Pong!")
    }
}