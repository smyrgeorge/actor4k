package io.github.smyrgeorge.actor4k.cluster.raft

import io.microraft.model.message.RaftMessage
import java.util.*

sealed interface ClusterRaftMessage {

    data class RaftPing(
        val id: UUID = UUID.randomUUID()
    ) : ClusterRaftMessage

    data class RaftProtocol(
        val message: RaftMessage,
    ) : ClusterRaftMessage

    data class RaftFollowerReady(
        val alias: String,
        val host: String,
        val port: Int
    ) : ClusterRaftMessage

    data class RaftNewLearner(
        val alias: String,
        val host: String,
        val port: Int
    ) : ClusterRaftMessage
}