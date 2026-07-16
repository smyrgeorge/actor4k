package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds

class SlowInitAccountActor(key: String) : AccountActor(key) {
    init {
        runBlocking {
            delay(500.milliseconds) // Simulate slow initialization
        }
    }
}
