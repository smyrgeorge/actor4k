package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Behavior

class ResourceHoldingAccountActor(key: String) : AccountActor(key) {
    companion object {
        var resourceClosed = false
    }

    // Simulate resource
    private var resourceOpen = false

    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        if (m is Protocol.Req && m.message == "OpenResource") {
            resourceOpen = true
            resourceClosed = false
        }
        return super.onReceive(m)
    }

    override suspend fun onShutdown() {
        if (resourceOpen) {
            // Close the resource
            resourceClosed = true
            resourceOpen = false
        }
        super.onShutdown()
    }

    // Reset for testing
    init {
        resourceClosed = false
    }
}
