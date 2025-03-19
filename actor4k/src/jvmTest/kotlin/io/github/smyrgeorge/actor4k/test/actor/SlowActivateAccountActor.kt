package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay

class SlowActivateAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onActivate(m: Protocol) {
        log.info("[${address()}] activate ($m)")
        delay(1000)
        activated++
    }

    companion object {
        var activated = 0
    }
}