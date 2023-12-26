package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.RaftEndpoint
import java.io.Serializable

data class ClusterRaftEndpoint(
    private val alias: String
) : RaftEndpoint, Serializable {
    override fun getId(): String = alias
}