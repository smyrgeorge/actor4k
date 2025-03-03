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
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class BasicActorLifecycleTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Create an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.INITIALISING)
        assertThat(actor.stats().messages).isZero()
    }

    @Test
    fun `Tell something to an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0001)
        ref.tell(AccountActor.Req("Ping!"))
        val actor: Actor = registry.get(ref as LocalRef)
        delay(100) // We have to wait a bit to ensure that the message is processed.
        assertThat(actor.stats().messages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Ask something an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0001)
        val res: AccountActor.Resp = ref.ask<AccountActor.Resp>(AccountActor.Req("Ping!"))
        assertThat(res.msg).isEqualTo("Pong!")
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.stats().messages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0001: String = "ACC0001"
    }
}