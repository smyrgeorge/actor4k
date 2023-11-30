package io.github.smyrgeorge.actor4k.actor.cluster

import io.github.oshai.kotlinlogging.KotlinLogging
import io.scalecube.cluster.Member
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import io.scalecube.cluster.Cluster as ScaleCluster

class Cluster {

    private val log = KotlinLogging.logger {}

    private lateinit var cluster: ScaleCluster
    private lateinit var node: Node

    fun register(n: Node): Cluster {
        node = n
        cluster = n.build()

        log.info { "Members: ${cluster.members()}" }
        log.info { "Addresses: ${cluster.addresses()}" }

        return this
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

}