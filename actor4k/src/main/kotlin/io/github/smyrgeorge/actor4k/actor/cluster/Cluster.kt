package io.github.smyrgeorge.actor4k.actor.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.ActorSystem
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.Member
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import io.scalecube.cluster.Cluster as ScaleCluster

class Cluster {

    private val log = KotlinLogging.logger {}
    private lateinit var cluster: ScaleCluster
    private lateinit var node: Node

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000)
                stats()
            }
        }
    }

    fun node(n: Node): Cluster {
        node = n
        return this
    }

    fun start(): Cluster {
        cluster = build()

        // Register cluster to the ActorSystem.
        ActorSystem.register(this)

        return this
    }

    private fun stats() {
        log.info { "Members: ${cluster.members()}" }
        log.info { "Addresses: ${cluster.addresses()}" }
    }

    fun members(): List<Member> =
        cluster.members().toList()

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


    private fun build(): ScaleCluster {
        val c: ClusterImpl = if (node.isSeed) seedOf() else nodeOf()
        return c
            .handler { node.handler }
            .transportFactory { TcpTransportFactory() }
            .startAwait()
    }

    private fun seedOf(): ClusterImpl =
        ClusterImpl()
            .config { it.memberAlias(node.alias) }
            .transport { it.port(node.seedPort) }

    private fun nodeOf(): ClusterImpl =
        ClusterImpl()
            .config { it.memberAlias(node.alias) }
            .membership { it.seedMembers(node.seedMembers) }

}