package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.model.message.RaftMessage

sealed interface ClusterRaftMessage {

    data class RaftProtocol(
        val message: RaftMessage,
    ) : ClusterRaftMessage

    data class RaftNewLearner(
        val alias: String,
        val host: String,
        val port: Int
    ) : ClusterRaftMessage
}