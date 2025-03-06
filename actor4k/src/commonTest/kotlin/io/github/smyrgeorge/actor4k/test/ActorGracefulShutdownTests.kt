package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ActorGracefulShutdownTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Create an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessAccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        delay(100)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        assertThat(actor.stats().receivedMessages).isZero()
    }

    @Test
    fun `Actor should graceful shutdown`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessAccountActor::class, ACC0000)
        assertThat(registry.size()).isEqualTo(1)
        repeat(2) { ref.tell(AccountActor.Req("Ping!")) }
        val actor: Actor = registry.get(ref as LocalRef)
        delay(1000)
        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHING)
        delay(1500)
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHED)
        assertThat(registry.size()).isZero()
        assertThat(actor.stats().receivedMessages).isEqualTo(2)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
    }
}