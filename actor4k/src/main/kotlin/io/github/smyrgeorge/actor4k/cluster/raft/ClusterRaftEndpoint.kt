package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.RaftEndpoint
import java.io.Serializable

data class ClusterRaftEndpoint(
    val alias: String,
    val host: String,
    val port: Int
) : RaftEndpoint, Serializable {
    override fun getId(): String = alias
}