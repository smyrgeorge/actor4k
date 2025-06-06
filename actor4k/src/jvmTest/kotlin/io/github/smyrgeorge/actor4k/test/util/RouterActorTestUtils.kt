package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import kotlinx.coroutines.delay

// Test protocol for messages
sealed class TestProtocol : RouterActor.Protocol() {
    data object Ping : TestProtocol()
}

// Test router implementation
class TestRouter(strategy: RouterActor.Strategy) : RouterActor<TestProtocol, RouterActor.Protocol.Ok>("test-router", strategy)

// Test worker implementation
class TestWorker(private val processingTime: Long = 0) :
    RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
    var messageCount = 0
        private set

    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        messageCount++
        if (processingTime > 0) {
            delay(processingTime)
        }
        return RouterActor.Protocol.Ok
    }
}

// Test protocol with multiple message types
sealed class TestProtocolWithMultipleMessages : RouterActor.Protocol() {
    data object Ping : TestProtocolWithMultipleMessages()
    data class Echo(val message: String) : TestProtocolWithMultipleMessages()
}

// Test router with multiple message types
class TestRouterWithMultipleMessages(strategy: RouterActor.Strategy) :
    RouterActor<TestProtocolWithMultipleMessages, RouterActor.Protocol.Ok>("test-router-multiple", strategy)

// Test worker with multiple message types
class TestWorkerWithMultipleMessages :
    RouterActor.Worker<TestProtocolWithMultipleMessages, RouterActor.Protocol.Ok>() {

    var pingCount = 0
        private set

    var lastEchoMessage: String = ""
        private set

    override suspend fun onReceive(m: TestProtocolWithMultipleMessages): RouterActor.Protocol.Ok {
        when (m) {
            is TestProtocolWithMultipleMessages.Ping -> pingCount++
            is TestProtocolWithMultipleMessages.Echo -> lastEchoMessage = m.message
        }
        return RouterActor.Protocol.Ok
    }
}

// Test worker that fails
class TestWorkerThatFails : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        throw RuntimeException("Simulated failure")
    }
}

// Test worker that fails occasionally
class TestWorkerThatFailsOccasionally : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
    var attemptCount: Int = 0
        private set

    var failureCount: Int = 0
        private set

    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        attemptCount++

        // Fail on every other message
        if (attemptCount % 2 == 0) {
            failureCount++
            throw RuntimeException("Simulated occasional failure")
        }

        return RouterActor.Protocol.Ok
    }
}

// Test worker that fails only on the first message
class TestWorkerThatFailsOnce : RouterActor.Worker<TestProtocol, RouterActor.Protocol.Ok>() {
    var messageCount: Int = 0
        private set

    var attemptCount: Int = 0
        private set

    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        attemptCount++

        // Fail only on the first message
        if (attemptCount == 1) {
            throw RuntimeException("Simulated one-time failure")
        }

        messageCount++
        return RouterActor.Protocol.Ok
    }
}