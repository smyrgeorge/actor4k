package io.github.smyrgeorge.actor4k.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.Member
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.HashRing
import org.ishugaliy.allgood.consistent.hash.hasher.DefaultHasher
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.*
import kotlin.jvm.optionals.getOrNull
import io.scalecube.cluster.Cluster as ScaleCubeCluster

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Cluster(
    private val node: Node,
    private val stats: Stats,
    private val cluster: ScaleCubeCluster,
    private val ring: ConsistentHash<ServerNode>
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

    fun members(): List<Member> = cluster.members().toList()

    suspend fun gossip(message: Message) {
        cluster.spreadGossip(message).awaitFirstOrNull()
    }

    suspend fun tell(receiver: List<Address>, message: Message) {
        cluster.send(receiver, message).awaitFirstOrNull()
    }

    suspend fun tell(member: Member, message: Envelope<*>) {
        // If the message needs to be delivered to the same service (that triggered the call),
        // we just directly call the [onMessage] handler function,
        // and thus we do not send data across the wire.
        if (member.alias() == cluster.member().alias()) {
            stats.message()
            node.onMessage(message)
            return
        }

        val msg = Message.fromData(message)
        cluster.send(member, msg).awaitFirstOrNull()
    }

    suspend fun tell(key: String, message: Envelope<*>) {
        val member: Member = memberOf(key)
        return tell(member, message)
    }

    suspend fun tell(key: Any, message: Envelope<*>) =
        tell(key.hashCode().toString(), message)

    suspend fun <T> ask(member: Member, message: Envelope<*>): Envelope<T> {
        val correlationId = UUID.randomUUID().toString()
        val msg = Message.builder().data(message).correlationId(correlationId).build()
        return cluster.requestResponse(member, msg).awaitSingle().data() as? Envelope<T>
            ?: error("Could not cast to the requested type.")
    }

    suspend fun <T> ask(key: String, message: Envelope<*>): Envelope<T> {
        val member: Member = memberOf(key)
        return ask(member, message)
    }

    suspend fun <T> ask(key: Any, message: Envelope<*>): Envelope<T> =
        ask(key.hashCode().toString(), message)

    fun memberOf(key: String): Member {
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
            // Build Cluster
            val (r, c, s) = build()

            // Build [Cluster]
            val cluster = Cluster(node = node, stats = s, cluster = c, ring = r)

            // Register cluster to the ActorSystem.
            ActorSystem.register(cluster)

            return cluster
        }

        private fun build(): Triple<ConsistentHash<ServerNode>, ScaleCubeCluster, Stats> {

            fun nodeOf(): ClusterImpl = ClusterImpl()
            fun seedOf(): ClusterImpl = ClusterImpl().transport { it.port(node.seedPort) }

            fun Member.toServerNode(): ServerNode {
                val address = addresses().first()
                return ServerNode(alias(), address.host(), address.port())
            }

            // Initialize stats object here.
            val stats = Stats()

            // Build hash ring.
            val ring: ConsistentHash<ServerNode> = HashRing.newBuilder<ServerNode>()
                // Hash ring name.
                .name(node.namespace)
                // Hash function to distribute partitions.
                .hasher(DefaultHasher.METRO_HASH)
                .build()

            // Build cluster.
            val cluster: ScaleCubeCluster = (if (node.isSeed) seedOf() else nodeOf())
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler { MessageHandler(node = node, stats = stats, ring = ring) }
                .startAwait()

            // Append current node to hash ring.
            ring.add(cluster.member().toServerNode())

            return Triple(ring, cluster, stats)
        }
    }
}