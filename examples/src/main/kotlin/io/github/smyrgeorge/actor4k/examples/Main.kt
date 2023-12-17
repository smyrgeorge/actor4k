package io.github.smyrgeorge.actor4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.Envelope
import io.github.smyrgeorge.actor4k.cluster.Node
import io.scalecube.net.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.*

class Main

data class Ping(
    val id: UUID = UUID.randomUUID(),
    val message: String = "Ping!"
) : Serializable {
    fun toEnvelope() = Envelope(payload = this)
}

data class Pong(
    val id: UUID,
    val message: String = "Pong!"
) : Serializable {
    fun toEnvelope() = Envelope(payload = this)
}

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    val alias = System.getenv("ACTOR_NODE_ID") ?: "node-1"
    val isSeed = System.getenv("ACTOR_NODE_IS_SEED")?.toBoolean() ?: false
    val seedPort = System.getenv("ACTOR_NODE_SEED_PORT")?.toInt() ?: 61100
    val seedMembers = System.getenv("ACTOR_SEED_MEMBERS")?.split(",")?.map { Address.from(it) } ?: emptyList()

    val node: Node = Node
        .Builder()
        .alias(alias)
        .namespace("actor4k")
        .isSeed(isSeed)
        .seedPort(seedPort)
        .seedMembers(seedMembers)
        .onGossip {
            log.debug { "Received Gossip: $it" }
        }
        .onMessage<Ping> {
            log.debug { "Received message: $it" }
        }
        .onRequest<Ping, Pong> {
            log.debug { "Received request: $it" }
            Pong(it.payload.id).toEnvelope()
        }
        .onMembershipEvent {
            log.debug { "Received membership-event: $it" }
        }
        .build()

    val cluster: Cluster = Cluster
        .Builder()
        .node(node)
        .start()

    runBlocking {
        withContext(Dispatchers.IO) {
            delay(10_000)
            while (true) {
                val ping = Ping()
//                cluster.tell(ping.id, ping.toEnvelope())
                val pong = cluster.ask<Pong>(ping.id, ping.toEnvelope())
//                println("Ping: $ping :::: Pong: ${pong.payload}")
            }
        }
    }
}
