package io.github.smyrgeorge.actor4k.cluster.microbank

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.Actor.Message
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.ClusterNode
import io.github.smyrgeorge.actor4k.cluster.microbank.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import io.github.smyrgeorge.actor4k.util.extentions.getEnv
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.polymorphic

class AccountActor(
    override val key: String
) : Actor<Protocol, Protocol.Response>(key) {

    private val account = Account(key, Int.MIN_VALUE)

    override suspend fun onActivate(m: Protocol) {
        // Initialize the account balance here.
        // E.g. fetch the data from the DB.
        // In this case we will assume that the balance is equal to '0'.
        account.balance = 0
        log.info("Activated: $account")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        val res = when (m) {
            is Protocol.GetAccount -> account
            is Protocol.ApplyTx -> {
                account.balance += m.value
                account
            }
        }

        return Protocol.Account(res)
    }

    @Serializable
    data class Account(val accountNo: String, var balance: Int)

    sealed class Protocol : Message() {
        sealed class Response : Message.Response()

        @Serializable
        data class GetAccount(val accountNo: String) : Protocol()

        @Serializable
        data class ApplyTx(val accountNo: String, val value: Int) : Protocol()

        @Serializable
        data class Account(val account: AccountActor.Account) : Response()
    }
}

object MicroBank {
    val loggerFactory = SimpleLoggerFactory()

    @Suppress("unused")
    val log: Logger = loggerFactory.getLogger(this::class)

    fun main() {
        val proxy = (getEnv("ACTOR4K_CURRENT_NODE_IS_PROXY") ?: "false").toBooleanStrict()
        val nodes = (getEnv("ACTOR4K_NODES") ?: "bank-1::localhost:6000").split(",").map { ClusterNode.of(it) }
        val current = (getEnv("ACTOR4K_CURRENT_NODE") ?: "bank-1").let { alias -> nodes.first { it.alias == alias } }
        println(">>> Current node (proxy=$proxy): $current")

        val registry = ClusterActorRegistry()
            .factoryFor(AccountActor::class) { AccountActor(it) }

        val cluster = ClusterImpl(
            proxy = proxy,
            nodes = nodes,
            current = current,
            loggerFactory = loggerFactory,
            routing = {
                get("/api/account/{accountNo}") {
                    val accountNo: String = call.parameters["accountNo"] ?: error("Missing accountNo from path.")
                    val ref: ActorRef = ActorSystem.get(AccountActor::class, accountNo)
                    val res = ref.ask<Protocol.Account>(Protocol.GetAccount(accountNo)).getOrThrow()
                    call.respond(Json.encodeToString(res), null)
                }
                get("/api/account/{accountNo}/status") {
                    val accountNo: String = call.parameters["accountNo"] ?: error("Missing accountNo from path.")
                    val ref: ActorRef = ActorSystem.get(AccountActor::class, accountNo)
                    val res: Actor.Status = ref.status()
                    call.respond(Json.encodeToString(res), null)
                }
                get("/api/account/{accountNo}/stats") {
                    val accountNo: String = call.parameters["accountNo"] ?: error("Missing accountNo from path.")
                    val ref: ActorRef = ActorSystem.get(AccountActor::class, accountNo)
                    val res: Actor.Stats = ref.stats()
                    call.respond(Json.encodeToString(res), null)
                }
                get("/api/account/{accountNo}/shutdown") {
                    val accountNo: String = call.parameters["accountNo"] ?: error("Missing accountNo from path.")
                    val ref: ActorRef = ActorSystem.get(AccountActor::class, accountNo)
                    val res: Unit = ref.shutdown()
                    call.respond(Json.encodeToString(res), null)
                }
                post("/api/account/{accountNo}") {
                    val accountNo = call.parameters["accountNo"] ?: error("Missing accountNo from path.")
                    val body = call.receive<String>()
                    val req = Json.decodeFromString<Protocol.ApplyTx>(body)
                    val ref: ActorRef = ActorSystem.get(AccountActor::class, accountNo)
                    val res = ref.ask<Protocol.Account>(Protocol.ApplyTx(accountNo, req.value)).getOrThrow()
                    call.respond(Json.encodeToString(res), null)
                }
            },
            serialization = {
                polymorphic(Message::class) {
                    subclass(Protocol.GetAccount::class, Protocol.GetAccount.serializer())
                    subclass(Protocol.ApplyTx::class, Protocol.ApplyTx.serializer())
                }
                polymorphic(Message.Response::class) {
                    subclass(Protocol.Account::class, Protocol.Account.serializer())
                }
            },
        )

        // Start the actor system.
        ActorSystem
            .register(loggerFactory)
            .register(SimpleStats())
            .register(registry)
            .register(cluster)
            .start(wait = true)
    }
}
