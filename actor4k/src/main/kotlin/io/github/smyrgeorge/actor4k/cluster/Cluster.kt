package io.github.smyrgeorge.actor4k.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val stats: Stats,
    private val grpc: GrpcServer,
    private val swim: ScaleCubeCluster,
    private val ring: ConsistentHash<ServerNode>,
    private val clients: ConcurrentHashMap<String, GrpcClient>,
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
    fun clients(): Map<String, GrpcClient> = clients

    suspend fun gossip(message: Message) {
        swim.spreadGossip(message).awaitFirstOrNull()
    }

//    suspend fun tell(key: String, message: Envelope) {
//        val member: Member = memberOf(key)
//        return tell(member, message)
//    }
//
//    suspend fun tell(key: Any, message: Envelope) =
//        tell(key.hashCode().toString(), message)

//    suspend fun <T> ask(key: String, message: Envelope): Envelope<T> {
//        val member: Member = memberOf(key)
//        return ask(member, message)
//    }
//
//    suspend fun <T> ask(key: Any, message: Envelope<*>): Envelope<T> =
//        ask(key.hashCode().toString(), message)

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

            // Build the GRPC server.
            val grpc: GrpcServer = ServerBuilder
                .forPort(node.grpcPort)
                .addService(GrpcService())
                .build()
                .start()

            // Build hash ring.
            val ring: ConsistentHash<ServerNode> = HashRing.newBuilder<ServerNode>()
                // Hash ring name.
                .name(node.namespace)
                // Hash function to distribute partitions.
                .hasher(DefaultHasher.METRO_HASH)
                .build()

            // Build grpc clients HashMap.
            val clients = ConcurrentHashMap<String, GrpcClient>()

            // Build cluster.
            val cluster: ScaleCubeCluster = (if (node.isSeed) seedOf() else nodeOf())
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler { MessageHandler(node = node, stats = stats, ring = ring, clients) }
                .startAwait()

            // Current cluster member.
            val member = cluster.member()

            // Append current node to hash ring.
            ring.add(member.toServerNode())

            // Append current node to clients HashMap.
            clients[node.alias] = GrpcClient(member.addresses().first().host(), node.grpcPort)

            return Cluster(node, stats, grpc, cluster, ring, clients)
        }
    }
}