package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.retryBlocking
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import java.util.concurrent.ConcurrentHashMap


class ClusterRaftTransport(
    private val self: ClusterRaftEndpoint,
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>
) : Transport {

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        target as ClusterRaftEndpoint

        if (self == target) {
            error("Sanity check failed :: ${self.id} cannot send $message to itself!")
        }

        retryBlocking {
            val msg = ClusterRaftMessage.RaftProtocol(message)
            grpcClients.getOrPut(target.alias) { GrpcClient(target.host, target.port) }.request(msg)
        }
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean =
        ActorSystem.cluster.nodes().any { it.dc == endpoint.id }
}