package io.github.smyrgeorge.actor4k.actor.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.ActorSystem
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import io.scalecube.cluster.Cluster as ScaleCluster

@Suppress("unused")
class Cluster(
    private val node: Node,
    private val cluster: ScaleCluster
) {

    private val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000)
                stats()
            }
        }
    }

    private fun stats() {
        log.info { "Members: ${cluster.members()}" }
    }

    fun members(): List<Member> =
        cluster.members().toList()

    suspend fun gossip(message: Message) {
        cluster.spreadGossip(message).awaitFirstOrNull()
    }

    suspend fun tell(member: Member, message: Message) {
        cluster.send(member, message).awaitFirstOrNull()
    }

    suspend fun tell(address: Address, message: Message) {
        cluster.send(address, message).awaitFirstOrNull()
    }

    suspend fun ask(member: Member, message: Message): Message =
        cluster.requestResponse(member, message).awaitSingle()

    suspend fun ask(address: Address, message: Message): Message =
        cluster.requestResponse(address, message).awaitSingle()

    class Builder {

        private lateinit var node: Node

        fun node(n: Node): Builder {
            node = n
            return this
        }

        fun start(): Cluster {
            // Build [Cluster]
            val cluster = Cluster(node = node, cluster = build())

            // Register cluster to the ActorSystem.
            ActorSystem.register(cluster)

            return cluster
        }

        private fun build(): ScaleCluster {
            fun nodeOf(): ClusterImpl = ClusterImpl()
            fun seedOf(): ClusterImpl = ClusterImpl().transport { it.port(node.seedPort) }

            val c: ClusterImpl = if (node.isSeed) seedOf() else nodeOf()

            return c
                .config { it.memberAlias(node.alias) }
                .membership { it.namespace(node.namespace) }
                .membership { it.seedMembers(node.seedMembers) }
                .transportFactory { TcpTransportFactory() }
                .handler {
                    object : ClusterMessageHandler {
                        override fun onGossip(g: Message): Unit = node.onGossip(g)
                        override fun onMessage(m: Message): Unit = node.onMessage(m)
                        override fun onMembershipEvent(e: MembershipEvent): Unit = node.onMembershipEvent(e)
                    }
                }.startAwait()
        }
    }
}