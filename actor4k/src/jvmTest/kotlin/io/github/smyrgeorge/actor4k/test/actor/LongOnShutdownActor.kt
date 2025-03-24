package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay

class LongOnShutdownActor(key: String) : AccountActor(key) {
    companion object {
        var shutdownHookExecuted = false
        var shutdownDelay = 1000L
    }

    override suspend fun onShutdown() {
        delay(shutdownDelay) // Simulate long cleanup
        shutdownHookExecuted = true
    }

    // Reset for testing
    init {
        shutdownHookExecuted = false
    }
}
