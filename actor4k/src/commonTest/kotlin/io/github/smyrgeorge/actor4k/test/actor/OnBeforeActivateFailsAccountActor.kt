package io.github.smyrgeorge.actor4k.test.actor

class OnBeforeActivateFailsAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onBeforeActivate() {
        error("boom!")
    }
}