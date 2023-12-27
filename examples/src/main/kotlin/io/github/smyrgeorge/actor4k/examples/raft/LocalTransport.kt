package io.github.smyrgeorge.actor4k.examples.raft

import io.microraft.RaftEndpoint
import io.microraft.RaftNode
import io.microraft.RaftNodeStatus
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import java.util.concurrent.ConcurrentHashMap


class LocalTransport(private val self: RaftEndpoint) : Transport {

    private val nodes: ConcurrentHashMap<RaftEndpoint, RaftNode> = ConcurrentHashMap()

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        if (self == target) {
            error("${self.id} cannot send $message to itself!")
        }

        nodes[target]?.handle(message)
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean =
        nodes.containsKey(endpoint)


    fun discoverNode(node: RaftNode) {
        val endpoint = node.localEndpoint
        if (self == endpoint) {
            error("${self.id} cannot discover itself!")
        }

        val existing = nodes.putIfAbsent(endpoint, node)
        if (existing != null && existing != node && existing.status != RaftNodeStatus.TERMINATED) {
            error("$self already knows: $endpoint.");
        }
    }
}