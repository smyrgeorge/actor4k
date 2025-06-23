package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior
import kotlinx.coroutines.delay

open class SlowProcessingAccountActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        delay(1000)
        log.info("[${address()}] Received message: $m")
        return Behavior.Respond(Protocol.Resp("Pong!"))
    }
}