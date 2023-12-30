package io.github.smyrgeorge.actor4k.cluster.gossip

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Stats
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftEndpoint
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.model.message.RaftMessage
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import java.io.Serializable
import kotlin.system.exitProcess
import io.scalecube.cluster.ClusterMessageHandler as ScaleCubeClusterMessageHandler

class MessageHandler(
    private val node: Node,
    private val stats: Stats
) : ScaleCubeClusterMessageHandler {

    private val log = KotlinLogging.logger {}

    private val rounds = 10
    private val delayPerRound: Long = 5_000

    private var initialGroupMembers = emptyList<ClusterRaftEndpoint>()

    sealed interface Protocol : Serializable {
        data object ReqInitialGroupMembers : Protocol {
            private fun readResolve(): Any = ReqInitialGroupMembers
        }

        data class ResInitialGroupMembers(val members: List<ClusterRaftEndpoint>) : Protocol
        data class RaftProtocol(val message: RaftMessage) : Protocol
    }

    @Suppress("unused")
    @OptIn(DelicateCoroutinesApi::class)
    private val job: Job = GlobalScope.launch(Dispatchers.IO) {
        for (i in 1..rounds) {
            log.info { "Waiting for other nodes to appear... [$i/$rounds]" }
            delay(delayPerRound)
            val nodes = ActorSystem.cluster.gossip.members()
            // If ore than one node found continue.
            if (nodes.size > 1) break
        }

        val nodes = ActorSystem.cluster.gossip.members()
        log.info { "We are a group of ${nodes.size} nodes (at least)." }

        val initialGroupMembers = if (nodes.isEmpty()) {
            log.error { "Sanity check failed :: No nodes found, but I was there..." }
            exitProcess(1)
        } else if (nodes.size == 1 && nodes.first().alias() == node.alias) {
            log.info { "Found ${nodes.size} node (myself). It's going to be a lonely day..." }
            listOf(ClusterRaftEndpoint(node.alias, node.host, node.grpcPort))
        } else {
            log.info { "Requesting initial group members form the network." }
            val self = ActorSystem.cluster.gossip.member().address()
            val msg = Message.builder().data(Protocol.ReqInitialGroupMembers).sender(self).build()
            for (i in 1..rounds) {
                log.info { "Waiting for response... [$i/$rounds]" }
                delay(delayPerRound)
                ActorSystem.cluster.gossip.spreadGossip(msg).awaitFirstOrNull()
                if (initialGroupMembers.isNotEmpty()) break
            }
            initialGroupMembers
        }

        if (initialGroupMembers.isEmpty()) {
            log.error { "No nodes found! Shutting down.." }
            exitProcess(1)
        }

        var allConnected = false
        for (i in 1..rounds) {
            log.info { "Waiting connection with all initial group members... [$i/$rounds]" }
            delay(delayPerRound)
            allConnected = initialGroupMembers
                .all { m -> ActorSystem.cluster.gossip.members().any { m.alias == it.alias() } }
            if (allConnected) break
        }

        if (!allConnected) {
            log.warn { "Could not establish connection with some of the initial group nodes." }
            exitProcess(1)
        }

        ActorSystem.cluster.startRaft(initialGroupMembers)
    }


    override fun onGossip(g: Message) {
        try {
            log.debug { "Received gossip: $g" }
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
                is Protocol.RaftProtocol -> Unit
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    override fun onMessage(m: Message) {
        try {
            log.debug { "Received message: $m" }
            when (val d = m.data<Protocol>()) {
                is Protocol.ResInitialGroupMembers -> {
                    initialGroupMembers.ifEmpty {
                        initialGroupMembers = d.members
                    }
                }

                is Protocol.RaftProtocol -> {
                    try {
                        ActorSystem.cluster.raft.handle(d.message)
                    } catch (e: UninitializedPropertyAccessException) {
                        log.debug { "Received message but the cluster is not yet initialized." }
                    }
                }

                Protocol.ReqInitialGroupMembers -> Unit
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
        }
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        when (e.type()) {
            MembershipEvent.Type.ADDED -> log.info { "New node found: ${e.member().alias()}" }
            MembershipEvent.Type.LEAVING -> log.info { "Node is leaving ${e.member().alias()}" }
            MembershipEvent.Type.REMOVED -> log.info { "Node removed: ${e.member().alias()}" }
            MembershipEvent.Type.UPDATED -> log.info { "Node updated: ${e.member().alias()}" }
        }
    }
}