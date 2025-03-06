package io.github.smyrgeorge.actor4k.test.actor

import kotlinx.coroutines.delay

class SlowActivateWithErrorInActivationAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] activate ($m)")
        delay(1000)
        error("boom!")
    }
}