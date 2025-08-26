package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that actors are closed after a period of inactivity based on the
 * ActorSystem.conf.actorExpiresAfter setting. This test uses a very short
 * expiration and cleanup interval and is intentionally in a separate class.
 */
class ActorExpirationTests {

    @Test
    fun `Actors should expire after a short period of inactivity`(): Unit = runBlocking {
        // Ensure any previous system state is reset
        ActorSystem.shutdown()

        // Configure very short expiration and cleanup intervals
        val conf = ActorSystem.Conf(
            actorExpiresAfter = 150.milliseconds,
            registryCleanupEvery = 50.milliseconds,
            actorActivateTimeout = 2.seconds,
            actorReplyTimeout = 1.seconds,
        )

        // Apply the short-lived configuration BEFORE initializing the Registry (affects cleanup scheduling)
        ActorSystem.conf(conf)

        // Ensure the shared Registry is initialized and use its registry
        val registry = Registry.registry

        // Start the system
        ActorSystem.start()

        // Create an actor and send a message to set lastMessageAt
        val ref: ActorRef = ActorSystem.get(AccountActor::class, "expiring-actor")
        val first = ref.ask(Protocol.Req("Ping!"))
        assertThat(first.isSuccess).isEqualTo(true)

        // Wait enough time so that the cleanup job runs after the actor has expired
        // actorExpiresAfter (150ms) + cleanup interval (50ms) + buffer
        delay(500)

        // Verify that the registry no longer holds the actor (expired and unregistered)
        assertThat(registry.size()).isZero()

        // Cleanup
        ActorSystem.shutdown()
        // Restore defaults to avoid impacting other tests
        ActorSystem.conf(ActorSystem.Conf())
    }
}
