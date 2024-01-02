package io.github.smyrgeorge.actor4k.cluster.shard

import kotlinx.serialization.Serializable

class Shard {
    @Serializable
    data class Key(val value: String)
}