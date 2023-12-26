package io.github.smyrgeorge.actor4k.examples.raft

import io.microraft.RaftEndpoint

data class LocalRaftEndpoint(private val alias: String) : RaftEndpoint {
    override fun getId(): String = alias
}