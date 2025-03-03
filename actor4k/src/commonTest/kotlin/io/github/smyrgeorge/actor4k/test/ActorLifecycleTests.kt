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
import io.github.smyrgeorge.actor4k.test.actor.NotRegisteredAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails

class ActorLifecycleTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Should fail for missing actor factory`(): Unit = runBlocking {
        assertFails { ActorSystem.get(NotRegisteredAccountActor::class, ACC0000) }
    }

    @Test
    fun `Create an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.INITIALISING)
        delay(100)
        assertThat(actor.status()).isEqualTo(Actor.Status.INITIALISING)
        assertThat(actor.stats().messages).isZero()
    }

    @Test
    fun `Evaluate actor's name and address`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val name: String = AccountActor::class.simpleName!!
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.name()).isEqualTo(name)
        assertThat(ref.address.toString()).isEqualTo("$name-$ACC0000")
        assertThat(ref.address.name).isEqualTo(name)
        assertThat(ref.address.key).isEqualTo(ACC0000)
    }

    @Test
    fun `Tell something to an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        ref.tell(AccountActor.Req("Ping!"))
        val actor: Actor = registry.get(ref as LocalRef)
        delay(100) // Ensure that the message is processed.
        assertThat(actor.stats().messages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Ask something an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val res: AccountActor.Resp = ref.ask<AccountActor.Resp>(AccountActor.Req("Ping!"))
        assertThat(res.msg).isEqualTo("Pong!")
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.stats().messages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Create and shutdown an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.INITIALISING)
        assertThat(actor.stats().messages).isZero()
        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHING)
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHED)
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `When shutdown() is triggered the mail box should be closed for receive`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.INITIALISING)
        assertThat(actor.stats().messages).isZero()
        actor.shutdown()
        assertFails { ref.tell(AccountActor.Req("Ping!")) }
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHED)
        assertThat(registry.size()).isZero()
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
    }
}