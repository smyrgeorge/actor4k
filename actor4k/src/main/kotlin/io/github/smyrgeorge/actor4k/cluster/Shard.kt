package io.github.smyrgeorge.actor4k.cluster

class Shard {
    data class Key(val value: String) {
        companion object {
            fun of(value: String) = Key(value)
        }
    }
}