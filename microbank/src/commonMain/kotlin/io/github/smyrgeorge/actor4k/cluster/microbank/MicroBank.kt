package io.github.smyrgeorge.actor4k.cluster.microbank

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.Actor.Message
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.microbank.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import io.github.smyrgeorge.actor4k.util.extentions.getEnv
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.polymorphic

object MicroBank {
    val loggerFactory = SimpleLoggerFactory()
    val log: Logger = loggerFactory.getLogger(this::class)

    fun main() {
        val proxy = (getEnv("ACTOR4K_CURRENT_NODE_IS_PROXY") ?: "false").toBooleanStrict()
        val nodes = (getEnv("ACTOR4K_NODES") ?: "bank-1::localhost:6000").split(",").map { ClusterNode.of(it) }
        val current = (getEnv("ACTOR4K_CURRENT_NODE") ?: "bank-1").let { alias ->
            nodes.firstOrNull { it.alias == alias }
                ?: error("The current node '$alias' should also be in the list of nodes.")
        }

        log.info("Current node (proxy=$proxy): $current")

        val registry = ClusterActorRegistry()
            .factoryFor(AccountActor::class) { AccountActor(it) }

        val cluster = ClusterImpl(
            proxy = proxy,
            nodes = nodes,
            current = current,
            loggerFactory = loggerFactory,
            routing = {
                get("/api/health") {
                    call.respond("OK", null)
                }
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
            .register(registry)
            .register(cluster)
            .start(wait = true)
    }
}
