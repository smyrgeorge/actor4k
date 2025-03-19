package io.github.smyrgeorge.actor4k.test.actor

class InitMethodFailsAccountActor(override val key: String) : AccountActor(key) {
    init {
        error("boom!")
    }
}