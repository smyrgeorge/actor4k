package io.github.smyrgeorge.actor4k.examples.bank

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.shard.Shard
import io.github.smyrgeorge.actor4k.examples.bank.serde.Jackson
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.scalecube.net.Address
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
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

sealed interface Req {

    val accountNo: String

    @Serializable
    data class GetAccount(override val accountNo: String) : Req

    @Serializable
    data class ApplyTx(override val accountNo: String, val value: Int) : Req
}


@Serializable
data class Account(val accountNo: String, var balance: Int)

data class AccountActor(override val shard: Shard.Key, override val key: Key) : Actor(shard, key) {

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

    val alias = System.getenv("ACTOR4K_NODE_ID") ?: "bank-1"
    val host = System.getenv("ACTOR4K_NODE_HOST") ?: alias
    val httpPort = System.getenv("ACTOR4K_NODE_HTTP_PORT")?.toInt() ?: 9000
    val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 61100
    val gossipPort = System.getenv("ACTOR4K_NODE_GOSSIP_PORT")?.toInt() ?: 61000
    val seedMembers: List<Address> = (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "localhost:$grpcPort")
        .split(",").map { Address.from(it) }

    val conf = Cluster.Conf
        .Builder()
        .alias(alias)
        .host(host)
        .namespace("actor4k")
        .grpcPort(grpcPort)
        .gossipPort(gossipPort)
        .seedMembers(seedMembers)
        .build()

    log.info { conf }

    Cluster
        .Builder()
        .conf(conf)
        .build()
        .start()

    val om: ObjectMapper = Jackson.create()
    val app: RoutingHttpHandler = routes(
        "/api/account/{accountNo}" bind Method.GET to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val req = Req.GetAccount(accountNo)
                val ref = ActorRegistry.get(AccountActor::class, Actor.Key(accountNo))
                val res = ref.ask<Account>(req)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        },
        "/api/account/{accountNo}" bind Method.POST to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val req = om.readValue<Req.ApplyTx>(it.body.stream)
                val ref = ActorRegistry.get(AccountActor::class, Actor.Key(accountNo))
                val res = ref.ask<Account>(req)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        }
    )
    app.asServer(Netty(httpPort)).start().block()
}
