package io.github.smyrgeorge.actor4k.examples.bank

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.Node
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.grpc.Serde
import io.github.smyrgeorge.actor4k.proto.Cluster.RaftProtocol
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.toInstance
import io.microraft.model.message.RaftMessage
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

    val alias = System.getenv("ACTOR4K_NODE_ID") ?: "bank-1"
    val host = System.getenv("ACTOR4K_NODE_HOST") ?: alias
    val httpPort = System.getenv("ACTOR4K_NODE_HTTP_PORT")?.toInt() ?: 9000
    val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 61100
    val gossipPort = System.getenv("ACTOR4K_NODE_GOSSIP_PORT")?.toInt() ?: 61000
    val seedMembers: List<Address> = (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "localhost:$grpcPort")
        .split(",").map { Address.from(it) }

    val node: Node = Node
        .Builder()
        .alias(alias)
        .host(host)
        .namespace("actor4k")
        .grpcPort(grpcPort)
        .gossipPort(gossipPort)
        .seedMembers(seedMembers)
        .build()

    log.info { node }

    Cluster
        .Builder()
        .node(node)
        .build()
        .start()

    val om: ObjectMapper = Serde.Jackson.create()
    val app: RoutingHttpHandler = routes(
        "/api/raft/ping" bind Method.GET to {
            ActorSystem.cluster.stats.protocol()
            Response(Status.OK)
        },
        "/api/raft/protocol" bind Method.POST to {
            ActorSystem.cluster.stats.protocol()
            runBlocking {
                try {
                    val body: RaftProtocol = RaftProtocol.parseFrom(it.body.payload)
                    val req: RaftMessage = body.payload.toByteArray().toInstance()
                    ActorSystem.cluster.raft.handle(req)
                } catch (e: Exception) {
                    log.error(e) { e.message }
                }
                Response(Status.OK)
            }
        },
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
