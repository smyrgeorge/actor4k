package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class LongOnShutdownActor(key: String) : AccountActor(key) {
    companion object {
        var shutdownHookExecuted = false
        var shutdownDelay = 1000.milliseconds
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
