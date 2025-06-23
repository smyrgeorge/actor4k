package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior

class ErrorThrowingAccountActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        if (m is Protocol.Req && m.message == "THROW_ERROR") {
            throw RuntimeException("Error processing message")
        }
        return super.onReceive(m)
    }
}
