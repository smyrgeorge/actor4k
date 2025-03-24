package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class SlowInitAccountActor(key: String) : AccountActor(key) {
    init {
        runBlocking {
            delay(500) // Simulate slow initialization
        }
    }
}
