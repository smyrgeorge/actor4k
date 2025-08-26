package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior

class ThrowingDuringMessageProcessingAccountActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        if (m is Protocol.Req && m.message == "THROW") {
            throw RuntimeException("Simulated error during processing")
        }
        return super.onReceive(m)
    }
}
