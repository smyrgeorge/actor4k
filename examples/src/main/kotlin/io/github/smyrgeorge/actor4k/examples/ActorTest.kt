package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import kotlinx.coroutines.runBlocking

class ActorTest

data class Req(val msg: String)
data class Resp(val msg: String)

data class AccountActor(val shard: Shard.Key, val key: String) : Actor(shard, key) {
    override fun onReceive(m: Message): Any {
        val msg = m.cast<Req>()
        log.info { "[$name] Received message: $msg" }
        return Resp("Pong!")
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val a: Actor.Ref = ActorRegistry.get(AccountActor::class, "ACC0010")

        val req = Req(msg = "[tell] Hello World!")
        a.tell(req)

        val req2 = Req(msg = "[ask] Ping!")
        val r = a.ask<Resp>(req2)
        println(r)

        val a2: Actor.Ref.Local = ActorRegistry.get(AccountActor::class, "ACC0010") as Actor.Ref.Local
        println(a2.status())
        a2.stop()
        a2.tell(req)
    }
}