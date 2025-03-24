package io.github.smyrgeorge.actor4k.test.actor

class ShortLivedActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Protocol.Response {
        if (m is Protocol.Req && m.message == "Shutdown") {
            // Schedule self-shutdown
            shutdown()
        }
        return super.onReceive(m)
    }
}
