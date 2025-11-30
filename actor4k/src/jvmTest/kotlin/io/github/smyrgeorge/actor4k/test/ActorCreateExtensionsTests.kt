package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.SimpleMessage
import io.github.smyrgeorge.actor4k.util.extentions.actorOf
import io.github.smyrgeorge.actor4k.util.extentions.simpleActorOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ActorCreateExtensionsTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    // Test protocol for a simple counter-actor
    sealed interface CounterProtocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : CounterProtocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data object GetValue : Message<CurrentValue>()
        data object Increment : Message<CurrentValue>()
        data object Decrement : Message<CurrentValue>()
        data class CurrentValue(val value: Int) : Response()
    }

    @Test
    fun `Create a simple actor with actorOf`(): Unit = runBlocking {
        val counter = actorOf<Int, CounterProtocol, CounterProtocol.Response>(initial = 0) { state, message ->
            when (message) {
                is CounterProtocol.GetValue -> Unit
                is CounterProtocol.Increment -> state.value += 1
                is CounterProtocol.Decrement -> state.value -= 1
            }
            Behavior.Reply(CounterProtocol.CurrentValue(state.value))
        }

        // Test increment
        val result1 = counter.ask(CounterProtocol.Increment).getOrThrow()
        assertThat(result1).isInstanceOf(CounterProtocol.CurrentValue::class)
        assertThat(result1.value).isEqualTo(1)

        // Test increment again
        val result2 = counter.ask(CounterProtocol.Increment).getOrThrow()
        assertThat(result2.value).isEqualTo(2)

        // Test decrement
        val result3 = counter.ask(CounterProtocol.Decrement).getOrThrow()
        assertThat(result3.value).isEqualTo(1)

        // Test get value
        val result4 = counter.ask(CounterProtocol.GetValue).getOrThrow()
        assertThat(result4.value).isEqualTo(1)

        counter.shutdown()
        delay(100) // Allow the actor to shut down
        assertThat(counter.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    data object GetValue : SimpleMessage<Int>()
    data object Increment : SimpleMessage<Int>()
    data object Decrement : SimpleMessage<Int>()

    @Test
    fun `Create a simple actor with simpleActorOf`(): Unit = runBlocking {
        val counter = simpleActorOf(initial = 0) { state, message ->
            when (message) {
                is GetValue -> state
                is Increment -> state + 1
                is Decrement -> state - 1
                else -> state
            }
        }

        // Test increment
        val result1 = counter.ask(Increment).getOrThrow()
        assertThat(result1.value).isEqualTo(1)

        // Test increment again
        val result2 = counter.ask(Increment).getOrThrow()
        assertThat(result2.value).isEqualTo(2)

        // Test decrement
        val result3 = counter.ask(Decrement).getOrThrow()
        assertThat(result3.value).isEqualTo(1)

        // Test get value
        val result4 = counter.ask(GetValue).getOrThrow()
        assertThat(result4.value).isEqualTo(1)

        counter.shutdown()
        delay(100) // Allow the actor to shut down
        assertThat(counter.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }
}