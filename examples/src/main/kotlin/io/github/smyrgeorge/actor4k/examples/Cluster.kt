package io.github.smyrgeorge.actor4k.examples

import io.scalecube.cluster.Cluster
import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.cluster.Member
import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking

class Cluster

fun main(args: Array<String>) {
    val alias = System.getenv("ACTOR_NODE_ID") ?: "node-1"
    val seedPort = System.getenv("ACTOR_NODE_SEED_PORT")?.toInt() ?: 61100

    // Build cluster.
    val cluster: Cluster = ClusterImpl().transport { it.port(seedPort) }
        .config { it.memberAlias(alias) }
        .transportFactory { TcpTransportFactory() }
        .handler {
            object : ClusterMessageHandler {
                override fun onMessage(message: Message) {
                    println("Received message: $message")

                    if (message.correlationId() != null
                        && message.header("x-is-reply") != null
                    ) {

                        val response = Message
                            .builder()
                            .correlationId(message.correlationId())
                            .header("x-is-reply", "t")
                            .data("Pong!")
                            .build()

                        runBlocking { it.send(message.sender(), response).awaitFirstOrNull() }
                    }
                }

                override fun onGossip(gossip: Message) {
                    println("Received message: $gossip")
                }

                override fun onMembershipEvent(event: MembershipEvent) {
                    println("Received membership-event: $event")
                }
            }
        }.startAwait()

    println(cluster.member())
    println(cluster.members())

    runBlocking {
        while (true) {
            val self: Member = cluster.member()
            val message: Message = Message.builder().correlationId("test_id").data("Ping!").build()
            val res: String = cluster.requestResponse(self, message).awaitSingle().data()

            println(res) // Prints "Ping!" and not "Pong!"

            // Block main thread.
            delay(1_000)
        }
    }
}
