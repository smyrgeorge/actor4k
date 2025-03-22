package io.github.smyrgeorge.actor4k.test.actor

class OnBeforeActivateFailsAccountActor(key: String) : AccountActor(key) {
    override suspend fun onBeforeActivate() {
        error("boom!")
    }
}