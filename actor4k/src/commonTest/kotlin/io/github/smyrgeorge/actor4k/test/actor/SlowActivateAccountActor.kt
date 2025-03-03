package io.github.smyrgeorge.actor4k.test.actor

class SlowActivateAccountActor(override val key: String) : AccountActor(key) {
    override suspend fun onBeforeActivate() {
        super.onBeforeActivate()
    }
}