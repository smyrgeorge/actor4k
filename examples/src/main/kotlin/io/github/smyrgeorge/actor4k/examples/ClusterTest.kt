package io.github.smyrgeorge.actor4k.examples

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.scalecube.net.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ClusterTest

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    val alias = System.getenv("ACTOR4K_NODE_ID") ?: "node-1"
    val seedPort = System.getenv("ACTOR4K_NODE_SWIM_PORT")?.toInt() ?: 61100
    val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 50051
    val seedMembers = (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "localhost:$seedPort")
        .split(",").map { Address.from(it) }

    val conf = Cluster.Conf
        .Builder()
        .alias(alias)
        .namespace("actor4k")
        .grpcPort(grpcPort)
        .seedMembers(seedMembers)
        .build()

    Cluster
        .Builder()
        .conf(conf)
        .build()
        .start()

    runBlocking {
        withContext(Dispatchers.IO) {

            delay(5_000)

            val req = Req("Hello!!")
            val ref = ActorRegistry.get(AccountActor::class, "ACC00011")
            println(ref)

            while (true) {
                delay(2_000)
//                val shard = UUID.randomUUID().toString()
//                val ping = Envelope.Ping(shard, UUID.randomUUID(), "Ping!")
//                val pong = cluster.msg(ping).getOrThrow<Envelope.Ping.Pong>()
//                log.info { "$ping :::: $pong" }
//                delay(2_000)
//                val res = ref.ask<Resp>(req)
//                log.info { res }
            }
        }
    }
}
