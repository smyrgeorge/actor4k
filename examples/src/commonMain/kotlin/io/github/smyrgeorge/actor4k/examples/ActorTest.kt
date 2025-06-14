package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

object ActorTest {
    fun run(): Unit = runBlocking {
        val loggerFactory = SimpleLoggerFactory()

        val registry = SimpleActorRegistry(loggerFactory)
            .factoryFor(AccountActor::class) { AccountActor(it) }

        // Start the actor system.
        ActorSystem
            .register(loggerFactory)
            .register(registry)
            .start()

        // [Create/Get] the desired actor from the registry.
        val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
        // [Tell] something to the actor (asynchronous operation).
        actor.tell(Protocol.Ping(message = "[tell] Hello World!")).getOrThrow()
        // [Ask] something to the actor (synchronous operation).
        val res = actor.ask(Protocol.Ping(message = "[ask] Ping!")).getOrThrow()
        println(res)

        // [Create] the desired actor.
        // We also need to manually [activate] the actor.
        val detached = AccountActor("DETACHED").apply { activate() }
        detached.tell(Protocol.Ping(message = "[ask] Ping!")).getOrThrow()
        // This actor will never close until we call the shutdown method.
        detached.shutdown()

        val a2 = ActorSystem.get(AccountActor::class, "ACC0010")
        println(a2.status())
        a2.shutdown()
        delay(1.seconds)

        ActorSystem.get(AccountActor::class, "ACC0030")

        val ping = Protocol.Ping(message = "[tell] Hello World!")
        a2.tell(ping).getOrThrow() // Will re-create the actor.
    }
}
