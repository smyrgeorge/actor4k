package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.impl.RouterActor
import io.github.smyrgeorge.actor4k.examples.RouterActorChild.TestProtocol

class RouterActorChild : Actor<TestProtocol, RouterActor.Protocol.Ok>(randomKey()) {
    override suspend fun onReceive(m: TestProtocol): RouterActor.Protocol.Ok {
        when (m) {
            TestProtocol.Test -> log.info("Received Test message: $m")
        }
        return RouterActor.Protocol.Ok
    }

    sealed class TestProtocol : RouterActor.Protocol() {
        data object Test : TestProtocol()
    }
}