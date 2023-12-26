package io.github.smyrgeorge.actor4k.cluster.swim

import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Stats
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftEndpoint
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.MembershipChangeMode
import io.microraft.RaftRole
import io.microraft.model.message.RaftMessage
import io.microraft.report.RaftGroupMembers
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
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>,
) : ScaleCubeClusterMessageHandler {

    override fun onGossip(g: Message) {
        node.onGossip(g)
        stats.gossip()
        when (g.data<Any>()) {
            is Cluster.Learner -> {
                val data = g.data<Cluster.Learner>()
                val role: RaftRole = ActorSystem.cluster.raft.report.join().result.role
                if (role == RaftRole.LEADER) {
                    val res: RaftGroupMembers = ActorSystem.cluster.raft.changeMembership(
                        /* endpoint = */ ClusterRaftEndpoint(data.alias),
                        /* mode = */ MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER,
                        /* expectedGroupMembersCommitIndex = */ 0
                    ).join().result
                }
            }
        }
    }

    override fun onMessage(m: Message) {
        when (m.data<Any>()) {
            is RaftMessage -> ActorSystem.cluster.raft.handle(m.data())
            else -> node.onMessage(m)
        }
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        node.onMembershipEvent(e)
        when (e.type()) {
            MembershipEvent.Type.ADDED -> added(e.member())
            MembershipEvent.Type.LEAVING, MembershipEvent.Type.REMOVED -> left(e.member())
            MembershipEvent.Type.UPDATED -> Unit
            else -> Unit
        }
    }

    private fun added(member: Member) {
        // Add member to hash-ring.
        ring.add(member.toServerNode())
        // Create the gRPC client.
        if (member.alias() != node.alias) {
            grpcClients[member.alias()] = GrpcClient(member.address().host(), node.grpcPort)
        }
    }

    private fun left(member: Member) {
        // Remove from hash-ring.
        ring.remove(member.toServerNode())

        // Shutdown client.
        grpcClients[member.alias()]?.close()

        // Remove the client from clients-hashmap.
        grpcClients.remove(member.alias())
    }

    private fun Member.toServerNode(): ServerNode =
        ServerNode(alias(), address().host(), address().port())

}