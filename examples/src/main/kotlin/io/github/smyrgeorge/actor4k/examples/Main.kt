package io.github.smyrgeorge.actor4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.scalecube.net.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class Main

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    val alias = System.getenv("ACTOR_NODE_ID") ?: "node-1"
    val isSeed = System.getenv("ACTOR_NODE_IS_SEED")?.toBoolean() ?: false
    val seedPort = System.getenv("ACTOR_NODE_SEED_PORT")?.toInt() ?: 61100
    val grpcPort = System.getenv("ACTOR_NODE_GRPC_PORT")?.toInt() ?: 50051
    val seedMembers = System.getenv("ACTOR_SEED_MEMBERS")?.split(",")?.map { Address.from(it) } ?: emptyList()

    val node: Node = Node
        .Builder()
        .alias(alias)
        .namespace("actor4k")
        .isSeed(isSeed)
        .seedPort(seedPort)
        .grpcPort(grpcPort)
        .seedMembers(seedMembers)
        .onGossip {
            log.debug { "Received Gossip: $it" }
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

            val req = Req("Hello!!")
            val ref = ActorRegistry.get(AccountActor::class, Actor.Key("ACC00011"))
            println(ref)

            while (true) {
                delay(2_000)
//                val shard = Shard.Key(UUID.randomUUID().toString())
//                val ping = Envelope.Ping(id = UUID.randomUUID(), message = "Ping!")
//                val pong = cluster.msg<Envelope.Pong>(shard, ping)
//                log.info { "$ping :::: $pong" }
                val res = ref.ask<Resp>(req)
                log.info { res }
            }
        }
    }
}
