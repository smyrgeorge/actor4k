package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.java.util.JLoggerFactory
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ActorTestKotlin

data class Req(val msg: String)
data class Resp(val msg: String)

data class AccountActor(override val key: String) : Actor(key) {

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] before-activate")
    }

    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] activate ($m)")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Req>()
        log.info("[${address()}] Received message: $msg")
        val res = Resp("Pong!")
        return r.value(res).build()
    }
}

fun main() = runBlocking {
    val registry = SimpleActorRegistry()
        .register(AccountActor::class) { AccountActor(it) }

    // Start the actor system.
    ActorSystem
        .register(JLoggerFactory())
        .register(SimpleStats())
        .register(registry)
        .start()

    val a: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")

    val req = Req(msg = "[tell] Hello World!")
    a.tell(req)

    val req2 = Req(msg = "[ask] Ping!")
    val r = a.ask<Resp>(req2)
    println(r)

    val a2: LocalRef = ActorSystem.get(AccountActor::class, "ACC0010") as LocalRef
    println(a2.status())
    a2.stop()
    delay(1000)

    val a3: LocalRef = ActorSystem.get(AccountActor::class, "ACC0030") as LocalRef

    a2.tell(req) // Will re-create the actor.
}
