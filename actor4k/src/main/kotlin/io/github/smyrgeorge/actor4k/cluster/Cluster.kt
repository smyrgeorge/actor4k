package io.github.smyrgeorge.actor4k.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.cluster.gossip.Metadata
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcClient
import io.github.smyrgeorge.actor4k.cluster.grpc.GrpcService
import io.github.smyrgeorge.actor4k.cluster.grpc.Serde
import io.github.smyrgeorge.actor4k.cluster.raft.Endpoint
import io.github.smyrgeorge.actor4k.cluster.raft.MemberManager
import io.github.smyrgeorge.actor4k.cluster.raft.StateMachine
import io.github.smyrgeorge.actor4k.cluster.raft.Transport
import io.grpc.netty.NettyServerBuilder
import io.microraft.RaftConfig
import io.microraft.RaftNode
import io.scalecube.cluster.ClusterImpl
import io.scalecube.net.Address
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.HashRing
import org.ishugaliy.allgood.consistent.hash.hasher.DefaultHasher
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.optionals.getOrNull
import io.grpc.Server as GrpcServer
import io.scalecube.cluster.Cluster as ScaleCubeCluster

class Cluster(
    val conf: Conf,
    val serde: Serde,
    val gossip: ScaleCubeCluster,
    val ring: ConsistentHash<ServerNode>,
    private val grpc: GrpcServer,
    private val grpcService: GrpcService
) {

    private val log = KotlinLogging.logger {}

    lateinit var raft: RaftNode
    lateinit var raftManager: MemberManager
    private val grpcClients: ConcurrentHashMap<String, GrpcClient> = ConcurrentHashMap()

    suspend fun shutdown() {
//        raftManager.shutdown()
//        raft.terminate()
        grpc.shutdown()
        grpcClients.values.forEach { it.close() }
        gossip.shutdown()
    }

    suspend fun msg(message: Envelope): Envelope.Response {
        val target = nodeOf(message.shard)
        return if (target.dc == conf.alias) {
            // Shortcut in case we need to send a message to self (same node).
            grpcService.request(message)
        } else {
            grpcClients[target.dc]?.request(message)
                ?: error("Could not find a gRPC client for member='${target.dc}'.")
        }
    }

    fun nodeOf(shard: String): ServerNode =
        ring.locate(shard).getOrNull()
            ?: error("Could not find node for shard='$shard', ring.size='${ring.size()}'.")

    fun registerGrpcClientFor(alias: String, host: String, port: Int) {
        grpcClients.getOrPut(alias) { GrpcClient(host, port) }
    }

    fun unregisterGrpcClient(alias: String) {
        grpcClients.remove(alias)
    }

    fun start(): Cluster {
        grpc.start()
        (gossip as ClusterImpl).startAwait()
        return this
    }

    fun startRaft(initialGroupMembers: List<Endpoint>): Cluster {
        log.info { "Starting raft, initialGroupMembers=$initialGroupMembers" }

        val endpoint = Endpoint(conf.alias, conf.host, conf.grpcPort)
        val config: RaftConfig = RaftConfig
            .newBuilder()
            .setLeaderElectionTimeoutMillis(10_000)
            .setLeaderHeartbeatTimeoutSecs(10)
            .setLeaderHeartbeatPeriodSecs(2)
            .setCommitCountToTakeSnapshot(5)
            .setAppendEntriesRequestBatchSize(1000)
            .setTransferSnapshotsFromFollowersEnabled(true)
            .build()
        raft = RaftNode
            .newBuilder()
            .setConfig(config)
            .setGroupId(conf.namespace)
            .setLocalEndpoint(endpoint)
            .setInitialGroupMembers(initialGroupMembers)
            .setTransport(Transport(endpoint))
            .setStateMachine(StateMachine(ring))
            .build()

        raft.start()

        raftManager = MemberManager(conf)

        return this
    }

    class Builder {

        private lateinit var conf: Conf
        private var serde: Serde = Serde.KotlinxProtobuf()

        fun conf(c: Conf): Builder {
            conf = c
            return this
        }

        fun serde(s: Serde): Builder {
            serde = s
            return this
        }

        fun build(): Cluster {
            // Build cluster.
            val gossip: ScaleCubeCluster = ClusterImpl()
                .transport { it.port(conf.gossipPort) }
                .config { it.memberAlias(conf.alias) }
                .config { it.metadata(Metadata(conf.grpcPort)) }
                .membership { it.namespace(conf.namespace) }
                .membership { it.seedMembers(conf.seedMembers.map { n -> n.address }) }
                .transportFactory { TcpTransportFactory() }
                .handler { MessageHandler(conf) }

            // Build the [GrpcService].
            val grpcService = GrpcService()

            // Build the gRPC server.
            val grpc: GrpcServer = NettyServerBuilder
                .forPort(conf.grpcPort)
                .addService(grpcService)
                .build()

            // Build hash ring.
            val ring: ConsistentHash<ServerNode> = hashRingOf(conf.namespace)

            // Built cluster
            val cluster = Cluster(conf, serde, gossip, ring, grpc, grpcService)

            return cluster
        }
    }

    data class Conf(
        val alias: String,
        val host: String,
        val namespace: String,
        val grpcPort: Int,
        val gossipPort: Int,
        val nodeManagement: NodeManagement,
        val seedMembers: List<Node>
    ) {

        data class Node(
            val alias: String,
            val address: Address
        ) {
            companion object {
                fun from(value: String): Node =
                    value.split("::").let { Node(it[0], Address.from(it[1])) }
            }
        }

        enum class NodeManagement {
            STATIC,
            DYNAMIC
        }

        class Builder {
            private lateinit var alias: String
            private lateinit var host: String
            private lateinit var namespace: String
            private var grpcPort: Int = 61100
            private var gossipPort: Int = 61000
            private var nodeManagement: NodeManagement = NodeManagement.STATIC
            private var seedMembers: List<Node> = emptyList()

            fun alias(v: String): Builder {
                alias = v
                return this
            }

            fun host(v: String): Builder {
                host = v
                return this
            }

            fun namespace(v: String): Builder {
                namespace = v
                return this
            }

            fun grpcPort(v: Int): Builder {
                grpcPort = v
                return this
            }

            fun gossipPort(v: Int): Builder {
                gossipPort = v
                return this
            }

            fun nodeManagement(v: NodeManagement): Builder {
                nodeManagement = v
                return this
            }

            fun seedMembers(v: List<Node>): Builder {
                seedMembers = v
                return this
            }

            fun build(): Conf = Conf(
                alias = alias,
                host = host,
                namespace = namespace,
                grpcPort = grpcPort,
                gossipPort = gossipPort,
                nodeManagement = nodeManagement,
                seedMembers = seedMembers
            )
        }
    }

    companion object {
        fun hashRingOf(namespace: String): ConsistentHash<ServerNode> = HashRing.newBuilder<ServerNode>()
            // Hash ring name.
            .name(namespace)
            // Hash function to distribute partitions.
            .hasher(DefaultHasher.METRO_HASH)
            .build()
    }
}
