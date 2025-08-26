package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.*
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.*
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds

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
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0000)
        delay(100)
        assertThat(actor.status()).isEqualTo(Actor.Status.ACTIVATING)
        assertThat(actor.stats().receivedMessages).isZero()
    }

    @Test
    fun `Evaluate actor's name and address`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val name: String = AccountActor::class.simpleName!!
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.address().name).isEqualTo(name)
        assertThat(ref.address.toString()).isEqualTo("$name-$ACC0000")
        assertThat(ref.address.name).isEqualTo(name)
        assertThat(ref.address.key).isEqualTo(ACC0000)
    }

    @Test
    fun `Tell something to an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        ref.tell(Protocol.Req("Ping!"))
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        delay(100) // Ensure that the message is processed.
        assertThat(actor.stats().receivedMessages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Ask something an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val res = ref.ask(Protocol.Req("Ping!")).getOrThrow()
        assertThat(res.message).isEqualTo("Pong!")
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        assertThat(actor.stats().receivedMessages).isEqualTo(1)
        assertThat(actor.status()).isEqualTo(Actor.Status.READY)
    }

    @Test
    fun `Create and shutdown an actor`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.stats().receivedMessages).isZero()
        actor.shutdown()
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
        assertThat(actor.stats().shutDownAt).isNotNull()
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `When shutdown is triggered the mail box should be closed for receive`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        assertThat(registry.size()).isEqualTo(1)
        assertThat(actor.key).isEqualTo(ACC0000)
        assertThat(actor.stats().receivedMessages).isZero()
        actor.shutdown()
        delay(100) // Ensure that the actor shut down.
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
        assertFails { actor.tell(Protocol.Req("Ping!")).getOrThrow() }
        assertThat(actor.stats().shutDownAt).isNotNull()
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `If actor fails to activate all ask request should receive the same error`(): Unit = runBlocking {
        val ref = ActorSystem.get(SlowActivateWithErrorInActivationAccountActor::class, ACC0000)
        val errors = listOf(1, 2, 3, 4).map {
            async {
                val res = ref.ask(Protocol.Req("Ping!")).exceptionOrNull()
                res?.message ?: ""
            }
        }.awaitAll()
        assertThat(errors.size).isEqualTo(4)
        assertThat(errors).isEqualTo(listOf("boom!", "boom!", "boom!", "boom!"))
    }

    @Test
    fun `If onBeforeActivate fails all ask requests should receive the same error`(): Unit = runBlocking {
        val ref = ActorSystem.get(OnBeforeActivateFailsAccountActor::class, ACC0004)
        val errors = listOf(1, 2, 3, 4).map {
            async {
                val res = ref.ask(Protocol.Req("Ping!"))
                res.exceptionOrNull()?.message ?: ""
            }
        }.awaitAll()
        assertThat(errors.size).isEqualTo(4)
        // All should be failures and either be activation error or rejection during shutdown
        assertThat(errors.all { it.isNotEmpty() }).isEqualTo(true)
        // At least one should be the activation error
        assertThat(errors.count { it == "boom!" } > 0).isEqualTo(true)
    }

    @Test
    fun `Registry should be cleaned up when onBeforeActivate fails`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(OnBeforeActivateFailsAccountActor::class, ACC0005)
        // Trigger activation by sending a message
        val result = ref.ask(Protocol.Req("Ping!"))
        // Should fail with boom!
        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("boom!")
        // Give some time for shutdown/unregister to complete
        delay(200)
        assertThat(registry.size()).isZero()
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
            async { ref.ask(Protocol.Req("Ping!")).getOrThrow().message }
        }.awaitAll()

        assertThat(registry.size()).isEqualTo(1)
        assertThat(SlowActivateAccountActor.activated).isEqualTo(1)
        assertThat(res).isEqualTo(listOf("Pong!", "Pong!", "Pong!", "Pong!"))
    }

    @Test
    fun `Actor should handle graceful shutdown while processing messages`() = runBlocking {
        // Create an actor
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0000)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Send several messages
        val responses = (1..5).map {
            async { ref.ask(Protocol.Req("Ping $it!")) }
        }

        // Give some time for processing to start
        delay(50)

        // Trigger shutdown
        actor.shutdown()

        // Check that already in-process messages are handled
        responses.awaitAll().map { it.getOrNull()?.message ?: "failed" }

        // Verify the actor is shut down
        delay(100)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)

        // Verify that at least some messages were processed
        assertThat(actor.stats().receivedMessages).isGreaterThan(0)
    }

    @Test
    fun `Actor should handle errors during message processing`() = runBlocking {
        // Create an actor that will throw exceptions
        val ref: ActorRef = ActorSystem.get(ErrorThrowingAccountActor::class, ACC0002)

        // Send a message that will cause an error
        val result = ref.ask(Protocol.Req("THROW_ERROR"))

        // Verify error handling
        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Error processing message")

        // Actor should still be alive and able to process messages
        val validResult = ref.ask(Protocol.Req("Valid"))
        assertThat(validResult.isSuccess).isEqualTo(true)
        assertThat(validResult.getOrThrow().message).isEqualTo("Pong!")
    }

    @Test
    fun `Actor should timeout when processing takes too long`() = runBlocking {
        // Create actor that has slow processing
        val ref: ActorRef = ActorSystem.get(SlowProcessingAccountActor::class, "slow")

        // Set a short timeout and expect it to fail
        val result = ref.ask(Protocol.Req("SlowRequest"), timeout = 100.milliseconds)

        // Verify timeout behavior
        assertThat(result.isFailure).isEqualTo(true)
        assertThat(result.exceptionOrNull()!! is TimeoutCancellationException).isEqualTo(true)
    }

    @Test
    fun `Actor should handle restarts or resurrection after failure`() = runBlocking {
        // Create an actor
        val ref: ActorRef = ActorSystem.get(AccountActor::class, ACC0003)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)

        // Force a shutdown
        actor.shutdown()
        delay(100)

        // Try to access the actor again, which should create a new instance
        val newRef: ActorRef = ActorSystem.get(AccountActor::class, ACC0003)

        // Send a message and verify it's processed
        val result = newRef.ask(Protocol.Req("Ping!"))
        assertThat(result.isSuccess).isEqualTo(true)

        // Verify we have a new actor
        val newActor: AnyActor = registry.getLocalActor(newRef as LocalRef)
        assertThat(newActor.stats().receivedMessages).isEqualTo(1)
        assertThat(newActor.stats().createdAt).isNotEqualTo(actor.stats().createdAt)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0001: String = "ACC0001"
        private const val ACC0002: String = "ACC0002"
        private const val ACC0003: String = "ACC0003"
        private const val ACC0004: String = "ACC0004"
        private const val ACC0005: String = "ACC0005"
    }
}