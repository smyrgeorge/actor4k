package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.RaftEndpoint
import java.io.Serializable

data class Endpoint(
    val alias: String,
    val host: String,
    val port: Int
) : RaftEndpoint, Serializable {
    override fun getId(): String = alias
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Endpoint

        return alias == other.alias
    }

    override fun hashCode(): Int {
        return alias.hashCode()
    }
}
