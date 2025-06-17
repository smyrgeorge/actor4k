package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import kotlinx.coroutines.delay

// Test protocol for messages
sealed interface TestProtocol : ActorProtocol {
    sealed class Message<R : ActorProtocol.Response> : TestProtocol, ActorProtocol.Message<R>()
    sealed class Response : ActorProtocol.Response()

    data object Ping : Message<Ok>()
    data class Echo(val message: String) : Message<Ok>()
    data object Ok : Response()
}

// Test router implementation
class TestRouter(strategy: Strategy) : RouterActor<TestProtocol, TestProtocol.Ok>("test-router", strategy)

// Test worker implementation
class TestWorker(private val processingTime: Long = 0) :
    RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {
    var messageCount = 0
        private set

    override suspend fun onReceive(m: TestProtocol): TestProtocol.Ok {
        messageCount++
        if (processingTime > 0) {
            delay(processingTime)
        }
        return TestProtocol.Ok
    }
}

// Test worker with multiple message types
class TestWorkerWithMultipleMessages :
    RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {

    var pingCount = 0
        private set

    var lastEchoMessage: String = ""
        private set

    override suspend fun onReceive(m: TestProtocol): TestProtocol.Ok {
        when (m) {
            is TestProtocol.Ping -> pingCount++
            is TestProtocol.Echo -> lastEchoMessage = m.message
        }
        return TestProtocol.Ok
    }
}

// Test worker that fails
class TestWorkerThatFails : RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {
    override suspend fun onReceive(m: TestProtocol): TestProtocol.Ok {
        throw RuntimeException("Simulated failure")
    }
}

// Test worker that fails occasionally
class TestWorkerThatFailsOccasionally : RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {
    var attemptCount: Int = 0
        private set

    var failureCount: Int = 0
        private set

    override suspend fun onReceive(m: TestProtocol): TestProtocol.Ok {
        attemptCount++

        // Fail on every other message
        if (attemptCount % 2 == 0) {
            failureCount++
            throw RuntimeException("Simulated occasional failure")
        }

        return TestProtocol.Ok
    }
}

// Test worker that fails only on the first message
class TestWorkerThatFailsOnce : RouterActor.Worker<TestProtocol, TestProtocol.Ok>() {
    var messageCount: Int = 0
        private set

    var attemptCount: Int = 0
        private set

    override suspend fun onReceive(m: TestProtocol): TestProtocol.Ok {
        attemptCount++

        // Fail only on the first message
        if (attemptCount == 1) {
            throw RuntimeException("Simulated one-time failure")
        }

        messageCount++
        return TestProtocol.Ok
    }
}