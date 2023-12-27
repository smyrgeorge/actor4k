package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.retryBlocking
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap


class ClusterRaftTransport(
    private val self: ClusterRaftEndpoint,
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>
) : Transport {

    private val log = KotlinLogging.logger {}

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        target as ClusterRaftEndpoint

        if (self == target) {
            return
//            ActorSystem.cluster.raft.handle(message)
//            error("Sanity check failed :: ${self.id} cannot send $message to itself!")
        }

        retryBlocking(times = 5) {
            val msg = ClusterRaftMessage.RaftProtocol(message)
            grpcClients.getOrPut(target.alias) { GrpcClient(target.host, target.port) }.request(msg)
        }
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean {
        endpoint as ClusterRaftEndpoint
        return try {
            runBlocking { ActorSystem.cluster.msg(endpoint.alias, ClusterRaftMessage.RaftPing()) }
            true
        } catch (e: Exception) {
            log.error(e) { "Could not send ping message to ${endpoint.alias}: ${e.message}" }
            false
        }
    }
}