package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ClusterTest

fun main() {
//    val log = KotlinLogging.logger {}

    val alias = System.getenv("ACTOR4K_NODE_ID") ?: "node-1"
    val swimPort = System.getenv("ACTOR4K_NODE_SWIM_PORT")?.toInt() ?: 61100
    val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 50051
    val seedMembers: List<Cluster.Conf.Node> =
        (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "$alias::localhost:$swimPort")
            .split(",")
            .map { Cluster.Conf.Node.from(it) }

    val conf = Cluster.Conf
        .Builder()
        .alias(alias)
        .namespace("actor4k")
        .grpcPort(grpcPort)
        .seedMembers(seedMembers)
        .build()

    val cluster = Cluster
        .Builder()
        .conf(conf)
        .build()

    ActorSystem
        .register(cluster)
        .start()

    runBlocking {
        withContext(Dispatchers.IO) {

            delay(5_000)

            val req = Req("Hello!!")
            val ref = ActorSystem.get(AccountActor::class, "ACC00011")
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
