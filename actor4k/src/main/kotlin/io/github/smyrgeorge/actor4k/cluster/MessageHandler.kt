package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import io.scalecube.cluster.ClusterMessageHandler as ScaleCubeClusterMessageHandler

class MessageHandler(
    private val node: Node,
    private val stats: Stats,
    private val ring: ConsistentHash<ServerNode>
) : ScaleCubeClusterMessageHandler {

    // Number of reply workers.
    private val replyWorkers = 10

    private data class Reply(val sender: List<Address>, val message: Message)

    private val replies = Channel<Reply>(capacity = Channel.UNLIMITED)

    init {
        // Start n workers (async), responsible to send the reply messages.
        repeat(replyWorkers) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                replies.consumeEach {
                    // TODO: find another way (do not use ActorSystem).
                    ActorSystem.cluster.tell(it.sender, it.message)
                }
            }
        }
    }

    override fun onGossip(g: Message) {
        stats.gossip()
        node.onGossip(g)
    }

    override fun onMessage(m: Message) {
        stats.message()
        if (m.correlationId() == null) {
            // Handle a simple message.
            node.onMessage(m.data())
        } else if (m.correlationId() != null && m.header(X_IS_REPLY) != null) {
            // Handle a response (to a request) message (same as a simple message).
            node.onMessage(m.data())
        } else {
            // Handle a request for response message.
            val resp: Envelope<*> = node.onRequest(m.data())
            val message = Message.builder()
                .correlationId(m.correlationId())
                .header(X_IS_REPLY, "t")
                .data(resp)
                .build()
            runBlocking { replies.send(Reply(m.sender(), message)) }
        }
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        fun Member.toServerNode(): ServerNode {
            val address = addresses().first()
            return ServerNode(alias(), address.host(), address.port())
        }

        when (e.type()) {
            MembershipEvent.Type.ADDED -> ring.add(e.member().toServerNode())
            MembershipEvent.Type.REMOVED -> ring.remove(e.member().toServerNode())
            MembershipEvent.Type.LEAVING -> ring.remove(e.member().toServerNode())
            MembershipEvent.Type.UPDATED -> Unit
            else -> error("Sanity check failed :: MembershipEvent.type was null.")
        }

        node.onMembershipEvent(e)
    }

    companion object {
        private const val X_IS_REPLY = "x-is-reply"
    }
}