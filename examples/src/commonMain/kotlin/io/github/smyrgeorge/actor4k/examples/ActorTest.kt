package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
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
        val msg = m.cast<Req>()
        log.info("[${address()}] onReceive: $msg")
        val res = Resp("Pong!")
        return r.value(res).build()
    }

    data class Req(val msg: String)
    data class Resp(val msg: String)
}

object Main {
    fun run() = runBlocking {
        val registry = SimpleActorRegistry()
            .register(AccountActor::class) { AccountActor(it) }

        // Start the actor system.
        ActorSystem
            .register(SimpleLoggerFactory())
            .register(SimpleStats())
            .register(registry)
            .start()

        // [Create/Get] the desired actor from the registry.
        val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
        // [Tell] something to the actor (asynchronous operation).
        actor.tell(AccountActor.Req(msg = "[tell] Hello World!"))
        // [Ask] something to the actor (synchronous operation).
        val res = actor.ask<AccountActor.Resp>(AccountActor.Req(msg = "[ask] Ping!"))
        println(res)

        val a2 = ActorSystem.get(AccountActor::class, "ACC0010")
        println(a2.status())
        a2.shutdown()
        delay(1000)

        ActorSystem.get(AccountActor::class, "ACC0030")

        val req = AccountActor.Req(msg = "[tell] Hello World!")
        a2.tell(req) // Will re-create the actor.
    }
}
