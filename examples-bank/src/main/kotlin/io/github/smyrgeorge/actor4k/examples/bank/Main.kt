package io.github.smyrgeorge.actor4k.examples.bank

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.grpc.Serde
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.scalecube.net.Address
import kotlinx.coroutines.runBlocking
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer

class Main

sealed class Req(open val accountNo: String) {
    data class GetAccount(override val accountNo: String) : Req(accountNo)
    data class ApplyTx(override val accountNo: String, val value: Int) : Req(accountNo)
}

data class Account(val accountNo: String, var balance: Int)

data class AccountActor(val shard: Shard.Key, val key: Key) : Actor(shard, key) {

    private val account = Account(key.value, 0)

    override fun onReceive(m: Message): Any {
        return when (val msg = m.cast<Req>()) {
            is Req.GetAccount -> account
            is Req.ApplyTx -> {
                account.balance += msg.value
                account
            }
        }
    }
}

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    val alias = System.getenv("ACTOR_NODE_ID") ?: "node-1"
    val httpPort = System.getenv("ACTOR_NODE_HTTP_PORT")?.toInt() ?: 9000
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

    Cluster
        .Builder()
        .node(node)
        .start()

    val om: ObjectMapper = Serde.Json.create()
    val app: RoutingHttpHandler = routes(
        "/api/account/{accountNo}" bind Method.GET to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val req = Req.GetAccount(accountNo)
                val ref = ActorRegistry.get(AccountActor::class.java, Actor.Key(accountNo))
                val res = ref.ask<Account>(req)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        },
        "/api/account/{accountNo}" bind Method.POST to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val req = om.readValue<Req.ApplyTx>(it.body.stream)
                val ref = ActorRegistry.get(AccountActor::class.java, Actor.Key(accountNo))
                val res = ref.ask<Account>(req)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        }
    )
    app.asServer(Netty(httpPort)).start().block()
}
