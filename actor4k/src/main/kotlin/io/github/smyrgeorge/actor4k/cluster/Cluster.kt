package io.github.smyrgeorge.actor4k.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcService
import io.github.smyrgeorge.actor4k.cluster.swim.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.grpc.ServerBuilder
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.Member
import io.scalecube.cluster.transport.api.Message
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.HashRing
import org.ishugaliy.allgood.consistent.hash.hasher.DefaultHasher
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import io.grpc.Server as GrpcServer
import io.scalecube.cluster.Cluster as ScaleCubeCluster

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Cluster(
    private val node: Node,
    val stats: Stats,
    private val swim: ScaleCubeCluster,
    private val ring: ConsistentHash<ServerNode>,
    private val grpc: GrpcServer,
    private val grpcService: GrpcService,
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>,
) {

    private val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5_000)
                stats()
            }
        }
    }

    private fun stats() {
        // Log [Stats].
        log.info { stats }
    }

    fun members(): List<Member> = swim.members().toList()

    suspend fun gossip(message: Message) {
        swim.spreadGossip(message).awaitFirstOrNull()
    }

    suspend fun <T : Envelope> ask(key: String, message: Envelope): T =
        ask(memberOf(key), message)

    private suspend fun <T : Envelope> ask(member: Member, message: Envelope): T {
        val res = if (member.alias() == node.alias) {
            // Shortcut in case we need to send a message to self (same node).
            grpcService.request(message)
        } else {
            grpcClients[member.alias()]?.request(message)
                ?: error("An error occurred. Could not find a gRPC client for member='${member.alias()}'.")
        }

        @Suppress("UNCHECKED_CAST")
        return res as? T ?: error("Could not cast to the requested type.")
    }

    private fun memberOf(key: String): Member {
        val node = ring.locate(key).getOrNull()
            ?: error("Could not find a valid recipient (probably empty), ring.size='${ring.size()}'.")
        return members().find { it.alias() == node.dc }
            ?: error("Could not find any member in the network with id='${node.dc}'.")
    }

    class Builder {

        private lateinit var node: Node

        fun node(n: Node): Builder {
            node = n
            return this
        }

        fun start(): Cluster {
            // Build [Cluster]
            val cluster = build()

            // Register cluster to the ActorSystem.
            ActorSystem.register(cluster)

            return cluster
        }

        private fun build(): Cluster {

            fun nodeOf(): ClusterImpl = ClusterImpl()
            fun seedOf(): ClusterImpl = ClusterImpl().transport { it.port(node.seedPort) }

            fun Member.toServerNode(): ServerNode {
                val address = addresses().first()
                return ServerNode(alias(), address.host(), address.port())
            }

            // Initialize stats object here.
            val stats = Stats()

            // Build the [GrpcService].
            val grpcService = GrpcService()

            // Build the gRPC server.
            val grpc: GrpcServer = ServerBuilder
                .forPort(node.grpcPort)
                .addService(grpcService)
                .build()
                .start()

            // Build gRPC clients HashMap.
            val grpcClients = ConcurrentHashMap<String, GrpcClient>()

            // Build hash ring.
            val ring: ConsistentHash<ServerNode> = HashRing.newBuilder<ServerNode>()
                // Hash ring name.
                .name(node.namespace)
                .partitionRate(1)
                // Hash function to distribute partitions.
                .hasher(DefaultHasher.METRO_HASH)
                .build()

            // Build cluster.
            val cluster: ScaleCubeCluster = (if (node.isSeed) seedOf() else nodeOf())
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler { MessageHandler(node = node, stats = stats, ring = ring, grpcClients) }
                .startAwait()

            // Current cluster member.
            val member = cluster.member()

            // Append current node to hash ring.
            ring.add(member.toServerNode())

            // Append current node to clients HashMap.
//            clients[node.alias] = GrpcClient(member.addresses().first().host(), node.grpcPort)

            return Cluster(node, stats, cluster, ring, grpc, grpcService, grpcClients)
        }
    }
}