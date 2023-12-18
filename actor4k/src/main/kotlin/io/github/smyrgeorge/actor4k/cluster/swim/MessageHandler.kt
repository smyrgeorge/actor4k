package io.github.smyrgeorge.actor4k.cluster.swim

import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Stats
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.concurrent.ConcurrentHashMap
import io.scalecube.cluster.ClusterMessageHandler as ScaleCubeClusterMessageHandler

class MessageHandler(
    private val node: Node,
    private val stats: Stats,
    private val ring: ConsistentHash<ServerNode>,
    private val clients: ConcurrentHashMap<String, GrpcClient>,
) : ScaleCubeClusterMessageHandler {

    override fun onGossip(g: Message) {
        stats.gossip()
        node.onGossip(g)
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        fun Member.toServerNode(): ServerNode {
            val address = addresses().first()
            return ServerNode(alias(), address.host(), address.port())
        }

        when (e.type()) {
            MembershipEvent.Type.ADDED -> {
                // Add to hash-ring.
                ring.add(e.member().toServerNode())
                // Create the grpc client for the newly discovered node.
                clients[e.member().alias()] = GrpcClient(e.member().addresses().first().host(), node.grpcPort)
            }

            MembershipEvent.Type.LEAVING, MembershipEvent.Type.REMOVED -> {
                // Remove from hash-ring.
                ring.remove(e.member().toServerNode())

                // Shutdown client.
                clients[e.member().alias()]?.close()

                // Remove the client from clients-hashmap.
                clients.remove(e.member().alias())
            }

            MembershipEvent.Type.UPDATED -> Unit
            else -> error("Sanity check failed :: MembershipEvent.type was null.")
        }

        node.onMembershipEvent(e)
    }
}