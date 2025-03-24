package io.github.smyrgeorge.actor4k.test.actor

class ThrowingDuringMessageProcessingActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Protocol.Response {
        if (m is Protocol.Req && m.message == "THROW") {
            throw RuntimeException("Simulated error during processing")
        }
        return super.onReceive(m)
    }
}
