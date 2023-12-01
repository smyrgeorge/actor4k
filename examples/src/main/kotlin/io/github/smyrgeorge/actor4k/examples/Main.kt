package io.github.smyrgeorge.actor4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cluster.Cluster
import io.github.smyrgeorge.actor4k.actor.cluster.Node
import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.*
import kotlin.random.Random

class Main

data class Msg(
    val uuid: UUID = UUID.randomUUID(),
    val message: String = "TEST MESSAGE"
) : Serializable

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    fun alias(): String =
        System.getenv("ACTOR_NODE_ID") ?: "node"

    fun isSeed(): Boolean =
        System.getenv("ACTOR_NODE_IS_SEED")?.toBoolean() ?: false

    fun seedPort(): Int =
        System.getenv("ACTOR_NODE_SEED_PORT")?.toInt() ?: 61100

    fun seedMembers(): List<Address> =
        System.getenv("ACTOR_SEED_MEMBERS")?.split(",")?.map { Address.from(it) }
            ?: emptyList()

    val handler = object : ClusterMessageHandler {
        override fun onMessage(message: Message) {
            log.info { "Received message: $message" }
        }

        override fun onGossip(gossip: Message) {
            log.info { "Received gossip: $gossip" }
        }

        override fun onMembershipEvent(event: MembershipEvent) {
            log.info { "Received membership-event: $event" }
        }
    }

    val node: Node = Node.Builder()
        .alias(alias())
        .isSeed(isSeed())
        .seedPort(seedPort())
        .seedMembers(seedMembers())
        .handler(handler)
        .build()

    val cluster = Cluster().node(node).start()

    runBlocking {
        withContext(Dispatchers.IO) {
            while (true) {
                delay(2000)
                val members: List<Member> = cluster.members()
                log.info { "Members: $members" }
                val member: Member = members[Random.nextInt(0, members.size)]
                cluster.tell(member, Message.fromData(Msg()))
            }
        }
    }
}
