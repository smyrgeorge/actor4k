package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior
import kotlinx.coroutines.delay

class TerminatingAccountActor(key: String) : AccountActor(key) {
    companion object {
        var shutdownHookExecuted = false
    }

    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        if (m is Protocol.Req) {
            return when (m.message) {
                "Terminate" -> Behavior.Terminate()
                "TerminateWithDelay" -> {
                    delay(500)
                    Behavior.Terminate()
                }
                else -> super.onReceive(m)
            }
        }
        return super.onReceive(m)
    }

    override suspend fun onShutdown() {
        shutdownHookExecuted = true
        super.onShutdown()
    }

    init {
        shutdownHookExecuted = false
    }
}
