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
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateWithErrorInActivationAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    fun `Create an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        delay(100)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        assertThat(actor.stats().processedMessages).isZero()
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
        assertThat(actor.stats().processedMessages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Ask something an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val res: AccountActor.Resp = ref.ask<AccountActor.Resp>(AccountActor.Req("Ping!"))
        assertThat(res.msg).isEqualTo("Pong!")
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(actor.stats().processedMessages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Create and shutdown an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        assertThat(actor.stats().processedMessages).isZero()
        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHING)
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHED)
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `When shutdown is triggered the mail box should be closed for receive`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: Actor = registry.get(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        assertThat(actor.stats().processedMessages).isZero()
        actor.shutdown()
        assertFails { ref.tell(AccountActor.Req("Ping!")) }
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.FINISHED)
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `If actor fails to activate all ask request should receive the same error`(): Unit = runBlocking {
        val ref = ActorSystem.get(SlowActivateWithErrorInActivationAccountActor::class, ACC0000)
        val errors = listOf(1, 2, 3, 4).map {
            async {
                val res = runCatching { ref.ask<AccountActor.Resp>(AccountActor.Req("Ping!")) }.exceptionOrNull()
                res?.message ?: ""
            }
        }.awaitAll()
        assertThat(errors.size).isEqualTo(4)
        assertThat(errors).isEqualTo(listOf("boom!", "boom!", "boom!", "boom!"))
    }

    @Test
    fun `An actor should be activated once`(): Unit = runBlocking {
        val ref = listOf(
            async { ActorSystem.get(SlowActivateAccountActor::class, ACC0001) },
            async { ActorSystem.get(SlowActivateAccountActor::class, ACC0001) },
            async { ActorSystem.get(SlowActivateAccountActor::class, ACC0001) },
            async { ActorSystem.get(SlowActivateAccountActor::class, ACC0001) },
        ).awaitAll().first()

        val res = listOf(1, 2, 3, 4).map {
            async { ref.ask<AccountActor.Resp>(AccountActor.Req("Ping!")).msg }
        }.awaitAll()

        assertThat(registry.size()).isEqualTo(1)
        assertThat(SlowActivateAccountActor.activated).isEqualTo(1)
        assertThat(res).isEqualTo(listOf("Pong!", "Pong!", "Pong!", "Pong!"))
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0001: String = "ACC0001"
    }
}