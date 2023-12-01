package io.github.smyrgeorge.actor4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cluster.Cluster
import io.github.smyrgeorge.actor4k.actor.cluster.Envelope
import io.github.smyrgeorge.actor4k.actor.cluster.Node
import io.scalecube.net.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.*

class Main

data class Msg(
    val id: UUID = UUID.randomUUID(),
    val message: String = "TEST MESSAGE"
) : Serializable

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
            log.info { "Received Gossip: $it" }
        }
        .onMessage {
            log.debug { "Received message: $it" }
        }
        .onMembershipEvent {
            log.info { "Received membership-event: $it" }
        }
        .build()

    val cluster: Cluster = Cluster
        .Builder()
        .node(node)
        .start()

    fun Msg.toEnvelope(): Envelope<*> = Envelope(payload = this)

    runBlocking {
        withContext(Dispatchers.IO) {
            delay(10_000)
            while (true) {
                val msg = Msg()
                cluster.tell(msg.id, msg.toEnvelope())
                delay(50)
            }
        }
    }
}
