package io.github.smyrgeorge.actor4k.cluster.gossip

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Stats
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftEndpoint
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import java.io.Serializable
import kotlin.system.exitProcess
import io.scalecube.cluster.ClusterMessageHandler as ScaleCubeClusterMessageHandler

class MessageHandler(
    private val node: Node,
    private val stats: Stats,
) : ScaleCubeClusterMessageHandler {

    private val log = KotlinLogging.logger {}

    private var initialGroupMembers = emptyList<ClusterRaftEndpoint>()

    private sealed interface Protocol : Serializable {
        data object ReqInitialGroupMembers : Protocol {
            private fun readResolve(): Any = ReqInitialGroupMembers
        }

        data class ResInitialGroupMembers(val members: List<ClusterRaftEndpoint>) : Protocol
    }

    @Suppress("unused")
    @OptIn(DelicateCoroutinesApi::class)
    private val job: Job = GlobalScope.launch(Dispatchers.IO) {
        delay(5_000) // Initial delay
        val rounds = 3
        var i = 0
        while (i < rounds) {
            log.info { "Waiting for other nodes to appear... [${i + 1}/$rounds]" }
            delay(10_000)
            val nodes = ActorSystem.cluster.gossip.members()
            // If ore than one node found continue.
            if (nodes.size > 1) break
            i++
        }

        val nodes = ActorSystem.cluster.gossip.members()
        val initialGroupMembers = if (nodes.isEmpty()) {
            log.error { "Sanity check failed :: No nodes found, but I was there..." }
            exitProcess(1)
        } else if (nodes.size == 1 && nodes.first().alias() == node.alias) {
            log.info { "Found ${nodes.size} node (myself). It's going to be lonely..." }
            listOf(ClusterRaftEndpoint(node.alias, node.host, node.grpcPort))
        } else {
            log.info { "Requesting initial group members form the network." }
            val self = ActorSystem.cluster.gossip.member().address()
            val msg = Message.builder().data(Protocol.ReqInitialGroupMembers).sender(self).build()
            i = 0
            while (i < rounds) {
                log.info { "Waiting for response... [${i + 1}/$rounds]" }
                ActorSystem.cluster.gossip.spreadGossip(msg).awaitFirstOrNull()
                delay(10_000)
                if (initialGroupMembers.isNotEmpty()) break
                i++
            }

            initialGroupMembers
        }

        if (initialGroupMembers.isEmpty()) {
            log.error { "No nodes found! Shutting down.." }
            exitProcess(1)
        }

        ActorSystem.cluster.startRaft(initialGroupMembers)
    }


    override fun onGossip(g: Message) {
        try {
            log.info { "Received gossip: $g" }
            when (g.data<Protocol>()) {
                Protocol.ReqInitialGroupMembers -> {
                    val members: List<ClusterRaftEndpoint> = initialGroupMembers.ifEmpty {
                        log.info { "Raft is not started will send the members found from the gossip protocol." }
                        ActorSystem.cluster.gossip.members().map {
                            val host = it.address().host()
                            val port = ActorSystem.cluster.node.grpcPort
                            ClusterRaftEndpoint(it.alias(), host, port)
                        }
                    }
                    val res = Protocol.ResInitialGroupMembers(members)
                    val msg = Message.builder().data(res).build()
                    runBlocking { ActorSystem.cluster.gossip.send(g.sender(), msg).awaitFirstOrNull() }
                }

                is Protocol.ResInitialGroupMembers -> Unit
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    override fun onMessage(m: Message) {
        try {
            log.info { "Received message: $m" }
            when (val d = m.data<Protocol>()) {
                is Protocol.ResInitialGroupMembers -> {
                    initialGroupMembers = d.members
                }

                Protocol.ReqInitialGroupMembers -> Unit
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        when (e.type()) {
            MembershipEvent.Type.ADDED -> added(e.member())
            MembershipEvent.Type.LEAVING, MembershipEvent.Type.REMOVED -> left(e.member())
            MembershipEvent.Type.UPDATED -> Unit
            else -> Unit
        }

        log.info { "NODES: ${ActorSystem.cluster.gossip.members()}" }
    }

    private fun added(member: Member) {
    }

    private fun left(member: Member) {
    }

//    private fun Member.toServerNode(): ServerNode =
//        ServerNode(alias(), address().host(), address().port())

}