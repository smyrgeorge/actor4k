package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior

class ShortLivedActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        if (m is Protocol.Req && m.message == "Shutdown") {
            // Schedule self-shutdown
            shutdown()
        }
        return super.onReceive(m)
    }
}
