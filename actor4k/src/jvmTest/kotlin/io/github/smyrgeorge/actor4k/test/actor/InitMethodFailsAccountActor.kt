package io.github.smyrgeorge.actor4k.test.actor

class InitMethodFailsAccountActor(key: String) : AccountActor(key) {
    init {
        error("boom!")
    }
}