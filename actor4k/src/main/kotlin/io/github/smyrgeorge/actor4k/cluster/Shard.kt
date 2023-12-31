package io.github.smyrgeorge.actor4k.cluster

import kotlinx.serialization.Serializable

class Shard {
    @Serializable
    data class Key(val value: String)
}