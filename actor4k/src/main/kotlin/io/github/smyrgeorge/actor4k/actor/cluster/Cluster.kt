package io.github.smyrgeorge.actor4k.actor.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.ActorSystem
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.ishugaliy.allgood.consistent.hash.ConsistentHash
import org.ishugaliy.allgood.consistent.hash.HashRing
import org.ishugaliy.allgood.consistent.hash.hasher.DefaultHasher
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import kotlin.jvm.optionals.getOrNull
import io.scalecube.cluster.Cluster as ScaleCluster

@Suppress("unused", "MemberVisibilityCanBePrivate")
class Cluster(
    private val node: Node,
    private val stats: Stats,
    private val cluster: ScaleCluster,
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
        log.info { stats }
        log.info { "Cluster members: ${cluster.members()}" }
        log.info { "Ring members: $ring" }
    }

    fun members(): List<Member> = cluster.members().toList()

    suspend fun gossip(message: Message) {
        cluster.spreadGossip(message).awaitFirstOrNull()
    }

    suspend fun tell(member: Member, message: Envelope<*>) {
        val msg = Message.fromData(message)
        // If the message needs to be delivered to the same service (that triggered the call),
        // we just directly call the [onMessage] handler function,
        // and thus we do not send data across the wire.
        if (member.alias() == cluster.member().alias()) {
            stats.plusMessage()
            node.onMessage(msg)
            return
        }

        cluster.send(member, msg).awaitFirstOrNull()
    }

    suspend fun tell(key: String, message: Envelope<*>) {
        val member: Member = memberOf(key)
        return tell(member, message)
    }

    suspend fun tell(key: Any, message: Envelope<*>) =
        tell(key.hashCode().toString(), message)

    suspend fun <T> ask(member: Member, message: Envelope<*>): Envelope<T> {
        println(message)
        val msg = Message.builder().data(message).correlationId(message.reqId.toString()).build()
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

    data class Stats(
        private var messages: Long = 0,
        private var gossipMessages: Long = 0,
        private var messagesPerSecond: Long = 0,
        private var gossipMessagesPerSecond: Long = 0,
    ) {

        init {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                while (true) {
                    val oldMessages = messages
                    val oldGossipMessages = gossipMessages
                    delay(1_000)
                    messagesPerSecond = messages - oldMessages
                    gossipMessagesPerSecond = gossipMessages - oldGossipMessages
                }
            }
        }

        fun plusMessage(): Long = messages++
        fun plusGossipMessage(): Long = gossipMessages++
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

        private fun build(): Triple<ConsistentHash<ServerNode>, ScaleCluster, Stats> {
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
            val cluster: ScaleCluster = (if (node.isSeed) seedOf() else nodeOf())
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler {
                    object : ClusterMessageHandler {
                        override fun onGossip(g: Message) {
                            stats.plusGossipMessage()
                            node.onGossip(g)
                        }

                        override fun onMessage(m: Message) {
                            stats.plusMessage()
                            node.onMessage(m)
                        }

                        override fun onMembershipEvent(e: MembershipEvent) {

                            when (e.type()) {
                                MembershipEvent.Type.ADDED -> ring.add(e.member().toServerNode())
                                MembershipEvent.Type.REMOVED -> ring.remove(e.member().toServerNode())
                                MembershipEvent.Type.LEAVING -> ring.remove(e.member().toServerNode())
                                MembershipEvent.Type.UPDATED -> Unit
                                else -> error("Sanity check failed :: MembershipEvent.type was null.")
                            }

                            node.onMembershipEvent(e)
                        }
                    }
                }.startAwait()

            // Append current node to hash ring.
            ring.add(cluster.member().toServerNode())

            return Triple(ring, cluster, stats)
        }
    }
}