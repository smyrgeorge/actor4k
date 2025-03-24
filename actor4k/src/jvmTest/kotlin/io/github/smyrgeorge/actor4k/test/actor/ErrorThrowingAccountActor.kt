package io.github.smyrgeorge.actor4k.test.actor

class ErrorThrowingAccountActor(key: String) : AccountActor(key) {
    override suspend fun onReceive(m: Protocol): Protocol.Response {
        if (m is Protocol.Req && m.message == "THROW_ERROR") {
            throw RuntimeException("Error processing message")
        }
        return super.onReceive(m)
    }
}
