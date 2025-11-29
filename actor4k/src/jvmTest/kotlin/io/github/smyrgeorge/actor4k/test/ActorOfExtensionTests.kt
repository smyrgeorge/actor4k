package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.actorOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds

class ActorOfExtensionTests {
    @Suppress("unused")
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Create a simple counter actor with actorOf`(): Unit = runBlocking {
        val counter = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0,
            key = "counter-simple"
        ) { state, message ->
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.Decrement -> {
                    state.value -= 1
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.Reset -> {
                    state.value = message.to
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
            }
        }

        counter.activate()
        delay(50) // Allow activation to complete

        // Test increment
        val result1 = counter.ask(CounterProtocol.Increment(5)).getOrThrow()
        assertThat(result1).isInstanceOf(CounterProtocol.CurrentValue::class)
        assertThat(result1.value).isEqualTo(5)

        // Test increment again
        val result2 = counter.ask(CounterProtocol.Increment(3)).getOrThrow()
        assertThat(result2.value).isEqualTo(8)

        // Test decrement
        val result3 = counter.ask(CounterProtocol.Decrement).getOrThrow()
        assertThat(result3.value).isEqualTo(7)

        // Test get value
        val result4 = counter.ask(CounterProtocol.GetValue).getOrThrow()
        assertThat(result4.value).isEqualTo(7)

        // Test reset
        val result5 = counter.ask(CounterProtocol.Reset(100)).getOrThrow()
        assertThat(result5.value).isEqualTo(100)

        counter.shutdown()
        delay(50)
        assertThat(counter.status()).isEqualTo(Actor.Status.SHUT_DOWN)
    }

    @Test
    fun `ActorOf with lifecycle hooks`(): Unit = runBlocking {
        var beforeActivateCalled = false
        var activateCalled = false
        var afterReceiveCalled = false
        var shutdownCalled = false
        var firstMessage: StateProtocol? = null

        val actor = actorOf<String, StateProtocol, StateProtocol.Response>(
            initialState = "initial",
            key = "lifecycle-actor",
            onBeforeActivate = {
                beforeActivateCalled = true
            },
            onActivate = { message ->
                activateCalled = true
                firstMessage = message
            },
            afterReceive = { _, result ->
                afterReceiveCalled = true
                assertThat(result.isSuccess).isTrue()
            },
            onShutdown = {
                shutdownCalled = true
            }
        ) { state, message ->
            when (message) {
                is StateProtocol.SetState -> {
                    state.value = message.state
                    Behavior.Reply(StateProtocol.StateResponse(state.value))
                }
                is StateProtocol.GetState -> {
                    Behavior.Reply(StateProtocol.StateResponse(state.value))
                }
            }
        }

        actor.activate()
        delay(50)

        // Send the first message
        val result1 = actor.ask(StateProtocol.SetState("new-state")).getOrThrow()
        assertThat(result1.state).isEqualTo("new-state")

        delay(50)

        // Verify lifecycle hooks were called
        assertThat(beforeActivateCalled).isTrue()
        assertThat(activateCalled).isTrue()
        assertThat(afterReceiveCalled).isTrue()
        assertThat(firstMessage).isNotNull().isInstanceOf(StateProtocol.SetState::class)

        actor.shutdown()
        delay(50)

        assertThat(shutdownCalled).isTrue()
    }

    @Test
    fun `ActorOf with tell operation`(): Unit = runBlocking {
        val counter = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0,
            key = "counter-tell"
        ) { state, message ->
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        counter.activate()
        delay(50)

        // Use tell for fire-and-forget
        counter.tell(CounterProtocol.Increment(10)).getOrThrow()
        delay(50) // Allow processing

        // Verify the state was updated
        val result = counter.ask(CounterProtocol.GetValue).getOrThrow()
        assertThat(result.value).isEqualTo(10)

        counter.shutdown()
    }

    @Test
    fun `ActorOf with custom capacity and buffer overflow`(): Unit = runBlocking {
        val actor = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0,
            key = "counter-capacity",
            capacity = 2,
            onMailboxBufferOverflow = BufferOverflow.DROP_OLDEST
        ) { state, message ->
            // Slow processing to fill the mailbox
            delay(100.milliseconds)
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        actor.activate()
        delay(50)

        // Send multiple messages quickly to test capacity
        actor.tell(CounterProtocol.Increment(1))
        actor.tell(CounterProtocol.Increment(2))
        actor.tell(CounterProtocol.Increment(3))

        delay(500) // Allow all messages to process

        actor.shutdown()
    }

    @Test
    fun `ActorOf with state management`(): Unit = runBlocking {
        val actor = actorOf<ComplexState, ComplexProtocol, ComplexProtocol.Response>(
            initialState = ComplexState("initial", 0, false),
            key = "complex-state"
        ) { state, message ->
            when (message) {
                is ComplexProtocol.UpdateName -> {
                    state.value.name = message.name
                    Behavior.Reply(ComplexProtocol.StateResponse(state.value.copy()))
                }
                is ComplexProtocol.IncrementCount -> {
                    state.value.count += message.by
                    Behavior.Reply(ComplexProtocol.StateResponse(state.value.copy()))
                }
                is ComplexProtocol.ToggleActive -> {
                    state.value.active = !state.value.active
                    Behavior.Reply(ComplexProtocol.StateResponse(state.value.copy()))
                }
                is ComplexProtocol.GetState -> {
                    Behavior.Reply(ComplexProtocol.StateResponse(state.value.copy()))
                }
            }
        }

        actor.activate()
        delay(50)

        // Test various state updates
        actor.ask(ComplexProtocol.UpdateName("test-actor")).getOrThrow()
        actor.ask(ComplexProtocol.IncrementCount(5)).getOrThrow()
        actor.ask(ComplexProtocol.ToggleActive).getOrThrow()

        val finalState = actor.ask(ComplexProtocol.GetState).getOrThrow()
        assertThat(finalState.state.name).isEqualTo("test-actor")
        assertThat(finalState.state.count).isEqualTo(5)
        assertThat(finalState.state.active).isTrue()

        actor.shutdown()
    }

    @Test
    fun `ActorOf with error handling`(): Unit = runBlocking {
        val actor = actorOf<Int, ErrorProtocol, ErrorProtocol.Response>(
            initialState = 0,
            key = "error-actor"
        ) { _, message ->
            when (message) {
                is ErrorProtocol.CauseError -> {
                    Behavior.Error(RuntimeException("Intentional error"))
                }
                is ErrorProtocol.ValidMessage -> {
                    Behavior.Reply(ErrorProtocol.SuccessResponse("Success"))
                }
            }
        }

        actor.activate()
        delay(50)

        // Test error behavior
        val errorResult = actor.ask(ErrorProtocol.CauseError)
        assertThat(errorResult.isFailure).isTrue()
        assertThat(errorResult.exceptionOrNull()).isNotNull()
            .isInstanceOf(RuntimeException::class)

        // Verify actor can still process valid messages after error
        val successResult = actor.ask(ErrorProtocol.ValidMessage).getOrThrow()
        assertThat(successResult).isInstanceOf(ErrorProtocol.SuccessResponse::class)

        actor.shutdown()
    }

    @Test
    fun `ActorOf with random key generation`(): Unit = runBlocking {
        val actor1 = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0
        ) { state, message ->
            when (message) {
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        val actor2 = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0
        ) { state, message ->
            when (message) {
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        actor1.activate()
        actor2.activate()
        delay(50)

        // Keys should be different (randomly generated)
        assertThat(actor1.key).isNotEqualTo(actor2.key)

        actor1.shutdown()
        actor2.shutdown()
    }

    @Test
    fun `ActorOf statistics tracking`(): Unit = runBlocking {
        val actor = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0,
            key = "stats-actor"
        ) { state, message ->
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        actor.activate()
        delay(50)

        // Send some messages
        actor.ask(CounterProtocol.Increment(1)).getOrThrow()
        actor.ask(CounterProtocol.Increment(2)).getOrThrow()
        actor.ask(CounterProtocol.Increment(3)).getOrThrow()

        delay(50)

        val stats = actor.stats()
        assertThat(stats.receivedMessages).isEqualTo(3)
        assertThat(stats.initializedAt).isNotNull()
        assertThat(stats.shutDownAt).isNull()

        actor.shutdown()
        delay(50)

        assertThat(actor.stats().shutDownAt).isNotNull()
    }

    @Test
    fun `ActorOf shutdown behavior`(): Unit = runBlocking {
        val actor = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 42,
            key = "shutdown-actor"
        ) { state, message ->
            when (message) {
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.Shutdown()
            }
        }

        actor.activate()
        delay(50)

        // Trigger shutdown via Behavior.Shutdown
        actor.tell(CounterProtocol.Increment(1)).getOrThrow()
        delay(100)

        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)

        // Verify actor cannot receive messages after shutdown
        assertFails { actor.tell(CounterProtocol.GetValue).getOrThrow() }
    }

    @Test
    fun `Multiple actorOf instances with different states`(): Unit = runBlocking {
        val counter1 = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 0,
            key = "counter-1"
        ) { state, message ->
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        val counter2 = actorOf<Int, CounterProtocol, CounterProtocol.Response>(
            initialState = 100,
            key = "counter-2"
        ) { state, message ->
            when (message) {
                is CounterProtocol.Increment -> {
                    state.value += message.by
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                is CounterProtocol.GetValue -> {
                    Behavior.Reply(CounterProtocol.CurrentValue(state.value))
                }
                else -> Behavior.None()
            }
        }

        counter1.activate()
        counter2.activate()
        delay(50)

        // Update both counters
        counter1.ask(CounterProtocol.Increment(10)).getOrThrow()
        counter2.ask(CounterProtocol.Increment(5)).getOrThrow()

        // Verify independent state management
        val result1 = counter1.ask(CounterProtocol.GetValue).getOrThrow()
        val result2 = counter2.ask(CounterProtocol.GetValue).getOrThrow()

        assertThat(result1.value).isEqualTo(10)
        assertThat(result2.value).isEqualTo(105)

        counter1.shutdown()
        counter2.shutdown()
    }

    // Test protocol for a simple counter-actor
    sealed interface CounterProtocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : CounterProtocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Increment(val by: Int) : Message<CurrentValue>()
        data object Decrement : Message<CurrentValue>()
        data object GetValue : Message<CurrentValue>()
        data class Reset(val to: Int = 0) : Message<CurrentValue>()
        data class CurrentValue(val value: Int) : Response()
    }

    // Test protocol for state management
    sealed interface StateProtocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : StateProtocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class SetState(val state: String) : Message<StateResponse>()
        data object GetState : Message<StateResponse>()
        data class StateResponse(val state: String) : Response()
    }

    // Test protocol for complex state management
    data class ComplexState(var name: String, var count: Int, var active: Boolean)

    sealed interface ComplexProtocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : ComplexProtocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class UpdateName(val name: String) : Message<StateResponse>()
        data class IncrementCount(val by: Int) : Message<StateResponse>()
        data object ToggleActive : Message<StateResponse>()
        data object GetState : Message<StateResponse>()
        data class StateResponse(val state: ComplexState) : Response()
    }

    // Test protocol for error handling
    sealed interface ErrorProtocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : ErrorProtocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data object CauseError : Message<Response>()
        data object ValidMessage : Message<SuccessResponse>()
        data class SuccessResponse(val message: String) : Response()
    }
}