package io.github.smyrgeorge.actor4k.cluster.gossip

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Stats
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftEndpoint
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftStateMachine
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.MembershipChangeMode
import io.microraft.RaftRole
import io.microraft.model.message.RaftMessage
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

    sealed interface Protocol : Serializable {
        data object ReqInitialGroupMembers : Protocol {
            private fun readResolve(): Any = ReqInitialGroupMembers
        }

        data class ResInitialGroupMembers(val members: List<ClusterRaftEndpoint>) : Protocol
        data class RaftProtocol(val message: RaftMessage) : Protocol
        data class RaftFollowerIsLeaving(val alias: String) : Protocol
    }

    @Suppress("unused")
    @OptIn(DelicateCoroutinesApi::class)
    private val job: Job = GlobalScope.launch(Dispatchers.IO) {
        delay(5_000) // Initial delay
        val rounds = 5
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
            log.debug { "Received gossip: $g" }
            when (val d = g.data<Protocol>()) {
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
                is Protocol.RaftFollowerIsLeaving -> {
                    val self = ActorSystem.cluster.raft
                    if (self.report.join().result.role == RaftRole.LEADER) {
                        val req = ClusterRaftStateMachine.NodeRemoved(d.alias)
                        try {
                            self.committedMembers.members.firstOrNull { it.id == req.alias }?.let {
                                // If the node is part of the initial members do not remove from raft state.
                                if (self.initialMembers.members.none { im -> im.id == req.alias }) {
                                    it as ClusterRaftEndpoint
                                    self.changeMembership(
                                        ClusterRaftEndpoint(req.alias, it.host, it.port),
                                        MembershipChangeMode.REMOVE_MEMBER,
                                        self.committedMembers.logIndex
                                    ).join().result
                                }
                            }
                        } catch (e: Exception) {
                            log.warn { e.message }
                        }
                        ActorSystem.cluster.raft.replicate<Unit>(req).join()
                    }
                }
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
                    ActorSystem.cluster.raft.handle(d.message)
                }

                Protocol.ReqInitialGroupMembers -> Unit
                is Protocol.RaftFollowerIsLeaving -> Unit
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

}