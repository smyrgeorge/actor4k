package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Suppress("unused")
class ActorTest

class AccountActor(override val key: String) : Actor(key) {

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Protocol>()
        log.info("[${address()}] onReceive: $msg")
        val res = when (msg) {
            is Protocol.Req -> Protocol.Req.Resp("Pong!")
        }
        return r.value(res).build()
    }

    sealed class Protocol : Message() {
        data class Req(val message: String) : Protocol() {
            data class Resp(val message: String)
        }
    }
}

object Main {
    fun run() = runBlocking {
        val registry = SimpleActorRegistry()
            .factoryFor(AccountActor::class) { AccountActor(it) }

        // Start the actor system.
        ActorSystem
            .register(SimpleLoggerFactory())
            .register(SimpleStats())
            .register(registry)
            .start()

        // [Create/Get] the desired actor from the registry.
        val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
        // [Tell] something to the actor (asynchronous operation).
        actor.tell(Protocol.Req(message = "[tell] Hello World!"))
        // [Ask] something to the actor (synchronous operation).
        val res = actor.ask<Protocol.Req.Resp>(Protocol.Req(message = "[ask] Ping!"))
        println(res)

        val a2 = ActorSystem.get(AccountActor::class, "ACC0010")
        println(a2.status())
        a2.shutdown()
        delay(1000)

        ActorSystem.get(AccountActor::class, "ACC0030")

        val req = Protocol.Req(message = "[tell] Hello World!")
        a2.tell(req) // Will re-create the actor.
    }
}
