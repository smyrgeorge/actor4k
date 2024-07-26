package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ActorTest

data class Req(val msg: String)
data class Resp(val msg: String)

data class AccountActor(
    override val shard: String,
    override val key: String
) : Actor(shard, key) {

    override suspend fun onActivate() {
        log.info { "[${address()}] activated" }
    }

    override suspend fun onFirstMessage(m: Message) {
        log.info { "[${address()}] first message: $m" }
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Req>()
        log.info { "[${address()}] Received message: $msg" }
        val res = Resp("Pong!")
        return r.value(res).build()
    }
}

fun main() {
    runBlocking {
        // Start the actor system.
        ActorSystem.start()

        val a: Actor.Ref = ActorRegistry.get(AccountActor::class, "ACC0010")

        val req = Req(msg = "[tell] Hello World!")
        a.tell(req)

        val req2 = Req(msg = "[ask] Ping!")
        val r = a.ask<Resp>(req2)
        println(r)

        val a2: Actor.Ref.Local = ActorRegistry.get(AccountActor::class, "ACC0010") as Actor.Ref.Local
        println(a2.status())
        a2.stop()
        delay(1000)

        val a3: Actor.Ref.Local = ActorRegistry.get(AccountActor::class, "ACC0030") as Actor.Ref.Local

        a2.tell(req) // Will re-create the actor.
    }
}
