package io.github.smyrgeorge.actor4k.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcService
import io.github.smyrgeorge.actor4k.cluster.grpc.Serde
import io.github.smyrgeorge.actor4k.cluster.raft.*
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.forEachParallel
import io.github.smyrgeorge.actor4k.util.retry
import io.grpc.ServerBuilder
import io.microraft.RaftConfig
import io.microraft.RaftNode
import io.scalecube.cluster.ClusterImpl
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.HashRing
import org.ishugaliy.allgood.consistent.hash.hasher.DefaultHasher
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import io.grpc.Server as GrpcServer
import io.scalecube.cluster.Cluster as ScaleCubeCluster

class Cluster(
    val node: Node,
    val stats: Stats,
    val serde: Serde,
    val gossip: ScaleCubeCluster,
    val ring: ConsistentHash<ServerNode>,
    private val grpc: GrpcServer,
    private val grpcService: GrpcService,
    private val grpcClients: ConcurrentHashMap<String, GrpcClient>
) {
    private val log = KotlinLogging.logger {}

    lateinit var raft: RaftNode
    lateinit var raftManager: ClusterRaftManager

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ActorSystem.Conf.clusterLogStats.toMillis())
//                stats()
            }
        }
    }

    suspend fun shutdown() {
//        raftManager.shutdown()
//        grpc.shutdown()
    }

    private fun stats() {
        // Log [Stats].
        log.info { stats }
    }

    suspend fun broadcast(message: ClusterRaftMessage): Unit =
        raft.committedMembers.members.filter { it.id != node.alias }.forEachParallel {
            try {
                it as ClusterRaftEndpoint
                retry(times = ActorSystem.Conf.clusterBroadcastRetries) {
                    grpcClientOf(it).request(message)
                }
            } catch (e: Exception) {
                log.error(e) { "Could not broadcast message to $it: ${e.message}" }
            }
        }

    suspend fun msg(message: Envelope): Envelope.Response {
        val target = nodeOf(message.shard)
        return if (target.dc == node.alias) {
            // Shortcut in case we need to send a message to self (same node).
            grpcService.request(message)
        } else {
            grpcClientOf(target.dc).request(message)
        }
    }

    fun nodeOf(shard: Shard.Key): ServerNode =
        ring.locate(shard.value).getOrNull()
            ?: error("Could not find node for shard='$shard', ring.size='${ring.size()}'.")

    private fun grpcClientOf(alias: String): GrpcClient =
        grpcClients[alias] ?: error("Could not find a gRPC client for member='$alias'.")

    private fun grpcClientOf(endpoint: ClusterRaftEndpoint): GrpcClient =
        grpcClients.getOrPut(endpoint.alias) { GrpcClient(endpoint.host, endpoint.port) }

    fun start(): Cluster {
        grpc.start()
        (gossip as ClusterImpl).startAwait()
        return this
    }

    fun startRaft(initialGroupMembers: List<ClusterRaftEndpoint>): Cluster {
        log.info { "Starting raft, initialGroupMembers=$initialGroupMembers" }

        val endpoint = ClusterRaftEndpoint(node.alias, node.host, node.grpcPort)
        val config: RaftConfig = RaftConfig
            .newBuilder()
            .setLeaderElectionTimeoutMillis(10_000)
            .setLeaderHeartbeatTimeoutSecs(10)
            .setLeaderHeartbeatPeriodSecs(2)
            .setCommitCountToTakeSnapshot(10)
            .setAppendEntriesRequestBatchSize(1000)
            .setTransferSnapshotsFromFollowersEnabled(true)
            .build()
        raft = RaftNode
            .newBuilder()
            .setConfig(config)
            .setGroupId(node.namespace)
            .setLocalEndpoint(endpoint)
            .setInitialGroupMembers(initialGroupMembers)
            .setTransport(ClusterRaftTransport(endpoint, grpcClients))
            .setStateMachine(ClusterRaftStateMachine(ring))
            .build()

        raft.start()

        raftManager = ClusterRaftManager()

        return this
    }

    class Builder {

        private lateinit var node: Node
        private var serde: Serde = Serde.Jackson()

        fun node(n: Node): Builder {
            node = n
            return this
        }

        fun serde(s: Serde): Builder {
            serde = s
            return this
        }

        fun build(): Cluster {
            // Initialize stats object here.
            val stats = Stats()

            // Build cluster.
            val gossip: ScaleCubeCluster = ClusterImpl()
                .transport { it.port(node.gossipPort) }
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler { MessageHandler(node, stats) }

            // Build the [GrpcService].
            val grpcService = GrpcService()

            // Build the gRPC server.
            val grpc: GrpcServer = ServerBuilder
                .forPort(node.grpcPort)
                .addService(grpcService)
                .build()

            // Build gRPC clients HashMap.
            val grpcClients = ConcurrentHashMap<String, GrpcClient>()

            // Build hash ring.
            val ring: ConsistentHash<ServerNode> = HashRing.newBuilder<ServerNode>()
                // Hash ring name.
                .name(node.namespace)
                // Hash function to distribute partitions.
                .hasher(DefaultHasher.METRO_HASH)
                .build()

            // Built cluster
            val cluster = Cluster(node, stats, serde, gossip, ring, grpc, grpcService, grpcClients)

            // Register cluster to the ActorSystem.
            ActorSystem.register(cluster)

            return cluster
        }
    }
}