package io.github.smyrgeorge.actor4k.microbank

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.system.registry.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.system.stats.ClusterStats
import io.github.smyrgeorge.actor4k.microbank.serde.Jackson
import io.github.smyrgeorge.actor4k.system.ActorSystem
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
import org.slf4j.LoggerFactory

class MicroBank

sealed interface Req {

    val accountNo: String

    @Serializable
    data class GetAccount(override val accountNo: String) : Req

    @Serializable
    data class ApplyTx(override val accountNo: String, val value: Int) : Req

    class Builder(private val ref: ActorRef) {
        suspend fun getAccount(accountNo: String): Account = ref.ask(GetAccount(accountNo))
        suspend fun applyTx(cmd: ApplyTx): Account = ref.ask(cmd)
    }

    companion object {
        suspend fun to(key: String): Builder {
            val ref = ActorSystem.get(AccountActor::class, key)
            return Builder(ref)
        }
    }
}

@Serializable
data class Account(val accountNo: String, var balance: Int)

data class AccountActor(
    override val shard: String,
    override val key: String
) : Actor(shard, key) {

    private val account = Account(key, Int.MIN_VALUE)

    override suspend fun onActivate(m: Message) {
        // Initialize the account balance here.
        // E.g. fetch the data from the DB.
        // In this case we will assume that the balance is equal to '0'.
        account.balance = 0
        log.info("Activated: $account")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val res = when (val msg = m.cast<Req>()) {
            is Req.GetAccount -> account
            is Req.ApplyTx -> {
                account.balance += msg.value
                account
            }
        }
        return r.value(res).build()
    }
}

fun main() {
    val log = LoggerFactory.getLogger("microbank")

    val alias = System.getenv("ACTOR4K_NODE_ID") ?: "bank-1"
    val host = System.getenv("ACTOR4K_NODE_HOST") ?: alias
    val httpPort = System.getenv("ACTOR4K_NODE_HTTP_PORT")?.toInt() ?: 9000
    val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 61100
    val gossipPort = System.getenv("ACTOR4K_NODE_GOSSIP_PORT")?.toInt() ?: 61000
    val seedMembers: List<ClusterImpl.Conf.Node> =
        (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "$alias::localhost:$gossipPort")
            .split(",")
            .map { ClusterImpl.Conf.Node.from(it) }

    val conf = ClusterImpl.Conf
        .Builder()
        .alias(alias)
        .host(host)
        .namespace("actor4k")
        .grpcPort(grpcPort)
        .gossipPort(gossipPort)
        .nodeManagement(ClusterImpl.Conf.NodeManagement.STATIC)
        .seedMembers(seedMembers)
        .build()

    log.info(conf.toString())

    val cluster = ClusterImpl
        .Builder()
        .conf(conf)
        .build()

    ActorSystem
        .register(ClusterStats())
        .register(ClusterActorRegistry())
        .register(cluster)
        .start()

    val om: ObjectMapper = Jackson.create()
    val app: RoutingHttpHandler = routes(
        "/api/account/{accountNo}" bind Method.GET to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val res = Req.to(accountNo).getAccount(accountNo)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        },
        "/api/account/{accountNo}" bind Method.POST to {
            runBlocking {
                val accountNo = it.path("accountNo") ?: error("Missing accountNo from path.")
                val req = om.readValue<Req.ApplyTx>(it.body.stream)
                val res = Req.to(accountNo).applyTx(req)
                Response(Status.OK).body(om.writeValueAsString(res))
            }
        }
    )
    app.asServer(Netty(httpPort)).start().block()
}
