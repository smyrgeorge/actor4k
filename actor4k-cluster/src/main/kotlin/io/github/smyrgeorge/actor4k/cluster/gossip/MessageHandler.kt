package io.github.smyrgeorge.actor4k.cluster.gossip

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.raft.Endpoint
import io.github.smyrgeorge.actor4k.cluster.util.toInstance
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.launch
import io.github.smyrgeorge.actor4k.util.retryBlocking
import io.microraft.model.message.RaftMessage
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import kotlin.system.exitProcess
import io.scalecube.cluster.ClusterMessageHandler as ScaleCubeClusterMessageHandler

class MessageHandler(private val conf: ClusterImpl.Conf) : ScaleCubeClusterMessageHandler {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }

    private val rounds = ActorSystem.conf.initializationRounds
    private val delayPerRound = ActorSystem.conf.initializationDelayPerRound
    private var initialGroupMembers = emptyList<Endpoint>()

    @Suppress("unused")
    private val job: Job = launch {
        if (conf.nodeManagement == ClusterImpl.Conf.NodeManagement.STATIC) {
            log.info("Starting cluster in STATIC mode. Any changes to the cluster will not be applied.")
            conf.seedMembers.forEach {
                cluster.ring.add(ServerNode(it.alias, it.address.host(), it.address.port()))
            }
            return@launch
        }

        for (i in 1..rounds) {
            log.info("Waiting for other nodes to appear... [$i/$rounds]")
            delay(delayPerRound)
            val nodes = cluster.gossip.members()
            // If ore than one node found continue.
            if (nodes.size > 1) break
        }

        val nodes = cluster.gossip.members()
        log.info("We are a group of ${nodes.size} nodes (at least).")

        val initialGroupMembers = if (nodes.isEmpty()) {
            log.error("Sanity check failed :: No nodes found, but I was there...")
            exitProcess(1)
        } else if (nodes.size == 1 && nodes.first().alias() == conf.alias) {
            log.info("Found ${nodes.size} node (myself). It's going to be a lonely day...")
            listOf(Endpoint(conf.alias, conf.host, conf.grpcPort))
        } else {
            log.info("Requesting initial group members form the network.")
            val self = cluster.gossip.member().address()
            val msg = Message.builder().data(Protocol.Gossip.ReqInitialGroupMembers).sender(self).build()
            for (i in 1..rounds) {
                log.info("Waiting for response... [$i/$rounds]")
                delay(delayPerRound)
                cluster.gossip.spreadGossip(msg).awaitFirstOrNull()
                if (initialGroupMembers.isNotEmpty()) break
            }
            initialGroupMembers
        }

        if (initialGroupMembers.isEmpty()) {
            log.error("No nodes found! Shutting down..")
            exitProcess(1)
        }

        var allConnected = false
        for (i in 1..rounds) {
            log.info("Waiting connection with all initial group members... [$i/$rounds]")
            delay(delayPerRound)
            allConnected = initialGroupMembers.all { m ->
                cluster.gossip.members().any { m.alias == it.alias() }
            }
            if (allConnected) break
        }

        if (!allConnected) {
            log.warn("Could not establish connection with some of the initial group nodes.")
            exitProcess(1)
        }

        cluster.startRaft(initialGroupMembers)
    }


    override fun onGossip(g: Message) {
        try {
            log.debug("Received gossip: {}", g)
            when (val d = g.data<Protocol.Gossip>()) {
                Protocol.Gossip.ReqInitialGroupMembers -> {
                    val members: List<Endpoint> = initialGroupMembers.ifEmpty {
                        log.info("Raft is not started will send the members found from the gossip protocol.")
                        cluster.gossip.members().map {
                            val host = it.address().host()
                            val port = cluster.conf.grpcPort
                            Endpoint(it.alias(), host, port)
                        }
                    }
                    val res = Protocol.Targeted.ResInitialGroupMembers(members)
                    val msg = Message.builder().data(res).build()
                    runBlocking { cluster.gossip.send(g.sender(), msg).awaitFirstOrNull() }
                }

                is Protocol.Gossip.LockShardsForJoiningNode ->
                    cluster.shardManager.lockShardsForJoiningNode(g.sender(), d)

                is Protocol.Gossip.LockShardsForLeavingNode ->
                    cluster.shardManager.lockShardsForLeavingNode(g.sender(), d)

                Protocol.Gossip.UnlockShards -> cluster.shardManager.unlockShards()
            }
        } catch (e: Exception) {
            log.error(e.message, e)
        }
    }

    override fun onMessage(m: Message) {
        try {
            log.debug("Received message: {}", m)
            when (val d = m.data<Protocol.Targeted>()) {
                is Protocol.Targeted.ResInitialGroupMembers -> {
                    initialGroupMembers.ifEmpty { initialGroupMembers = d.members }
                }

                is Protocol.Targeted.RaftProtocol -> {
                    try {
                        cluster.raft.handle(d.message)
                    } catch (e: UninitializedPropertyAccessException) {
                        log.debug("Received message but the cluster is not yet initialized.")
                    }
                }

                is Protocol.Targeted.ShardsLocked -> runBlocking { cluster.raftManager.send(d) }
                is Protocol.Targeted.ShardedActorsFinished -> runBlocking { cluster.raftManager.send(d) }
            }
        } catch (e: Exception) {
            log.error(e.message, e)
        }
    }

    override fun onMembershipEvent(e: MembershipEvent) {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") when (e.type()) {
            MembershipEvent.Type.ADDED -> {
                val m = e.member()
                log.info("New node found: $m")

                val metadata = ByteArray(e.newMetadata().remaining())
                    .also { e.newMetadata().get(it) }
                    .toInstance<Metadata>()

                retryBlocking {
                    cluster.registerGrpcClientFor(m.alias(), m.address().host(), metadata.grpcPort)
                }
            }

            MembershipEvent.Type.REMOVED -> {
                log.info("Node removed: ${e.member().alias()}")
                cluster.unregisterGrpcClient(e.member().alias())
            }

            MembershipEvent.Type.LEAVING -> log.info("Node is leaving ${e.member().alias()}")
            MembershipEvent.Type.UPDATED -> log.info("Node updated: ${e.member().alias()}")
        }
    }

    sealed interface Protocol : Serializable {
        sealed interface Gossip : Protocol {
            data object ReqInitialGroupMembers : Gossip {
                private fun readResolve(): Any = ReqInitialGroupMembers
            }

            data class LockShardsForJoiningNode(val alias: String, val host: String, val port: Int) : Gossip
            data class LockShardsForLeavingNode(val alias: String, val host: String, val port: Int) : Gossip
            data object UnlockShards : Gossip {
                private fun readResolve(): Any = UnlockShards
            }
        }

        sealed interface Targeted : Protocol {
            data class ShardsLocked(val alias: String) : Targeted
            data class ShardedActorsFinished(val alias: String) : Targeted
            data class ResInitialGroupMembers(val members: List<Endpoint>) : Targeted
            data class RaftProtocol(val message: RaftMessage) : Targeted
        }
    }
}
