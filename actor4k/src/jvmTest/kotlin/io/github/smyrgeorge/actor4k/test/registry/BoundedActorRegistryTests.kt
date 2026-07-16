package io.github.smyrgeorge.actor4k.test.registry

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.BoundedActorRegistry
import io.github.smyrgeorge.actor4k.system.registry.policy.MaxActorsPolicy
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class BoundedActorRegistryTests {

    @Test
    fun `MaxActorsPolicy should reject a non-positive limit`() {
        assertFailsWith<IllegalArgumentException> { MaxActorsPolicy(0) }
        assertFailsWith<IllegalArgumentException> { MaxActorsPolicy(-1) }
    }

    @Test
    fun `Should create actors normally while under the limit`(): Unit =
        withBoundedRegistry(maxActors = 5) { registry ->
            registry.get(AccountActor::class, "ACC1")
            registry.get(AccountActor::class, "ACC2")
            assertThat(registry.size()).isEqualTo(2)
        }

    @Test
    fun `Should still return already registered actors when at capacity`(): Unit =
        withBoundedRegistry(
            maxActors = 2,
            // Long expiry and cleanup so nothing is released during the test.
            conf = ActorSystem.Conf(actorExpiresAfter = 10.minutes, registryCleanupEvery = 10.minutes),
        ) { registry ->
            val ref = registry.get(AccountActor::class, "ACC1")
            registry.get(AccountActor::class, "ACC2")
            assertThat(registry.size()).isEqualTo(2)

            // Retrieving an already-registered actor must succeed even at capacity (no policy check).
            val again = registry.get(AccountActor::class, "ACC1")
            assertThat(again.address).isEqualTo(ref.address)
            assertThat(registry.size()).isEqualTo(2)
        }

    @Test
    fun `Should reject new actors when the max actors policy is exceeded`(): Unit =
        withBoundedRegistry(
            maxActors = 3,
            // Long expiry and cleanup so no actor can be released to make room.
            conf = ActorSystem.Conf(actorExpiresAfter = 10.minutes, registryCleanupEvery = 10.minutes),
        ) { registry ->
            // Fill the registry up to its limit.
            registry.get(AccountActor::class, "ACC1")
            registry.get(AccountActor::class, "ACC2")
            registry.get(AccountActor::class, "ACC3")
            assertThat(registry.size()).isEqualTo(3)

            // Creating one more must fail because no inactive actors can be released.
            val error = assertFailsWith<IllegalStateException> {
                registry.get(AccountActor::class, "ACC4")
            }

            // The error message must explain why the policy is exceeded.
            val message = error.message!!
            assertThat(message).contains("policy is exceeded")
            assertThat(message).contains("limit=3")

            // The registry never grew beyond the limit.
            assertThat(registry.size()).isEqualTo(3)
        }

    @Test
    fun `Should release expired actors to admit a new one when the policy is exceeded`(): Unit =
        withBoundedRegistry(
            maxActors = 2,
            conf = ActorSystem.Conf(
                actorExpiresAfter = 100.milliseconds,
                // A large cleanup interval so the ONLY cleanup that runs is the one the policy triggers.
                registryCleanupEvery = 10.minutes,
            ),
        ) { registry ->
            registry.get(AccountActor::class, "ACC1")
            registry.get(AccountActor::class, "ACC2")
            assertThat(registry.size()).isEqualTo(2)

            // Let the two actors pass their expiration window. Because the periodic cleanup is
            // effectively disabled, they remain in the registry until the policy forces a cleanup.
            delay(400.milliseconds)
            assertThat(registry.size()).isEqualTo(2)

            // Creating a new actor now exceeds the policy, which triggers an aggressive cleanup of the
            // expired actors, freeing capacity so the new actor can be admitted (does not throw).
            val ref = registry.get(AccountActor::class, "ACC3")
            assertThat(ref.address.key).isEqualTo("ACC3")

            // The registry stayed within its limit: the expired actors were released.
            assertThat(registry.size()).isLessThanOrEqualTo(2)
        }

    /**
     * Runs [block] against a freshly built [BoundedActorRegistry] with a [MaxActorsPolicy] of
     * [maxActors], installed as the active [ActorSystem] registry under the given [conf]. The
     * previous global registry and configuration are restored afterwards.
     */
    private fun withBoundedRegistry(
        maxActors: Int,
        conf: ActorSystem.Conf = ActorSystem.Conf(),
        block: suspend (BoundedActorRegistry) -> Unit,
    ): Unit = runBlocking {
        // Ensure the shared Registry (and its logger factory) is initialized so we can restore it.
        val shared = Registry.registry
        ActorSystem.shutdown()

        // The registry schedules its periodic cleanup using the conf present at construction time,
        // so the configuration must be applied *before* the registry is created.
        ActorSystem.conf(conf)

        val registry = BoundedActorRegistry(Registry.loggerFactory, MaxActorsPolicy(maxActors))
        registry.factoryFor(AccountActor::class) { AccountActor(it) }

        // Make the bounded registry the active one, then start the system.
        ActorSystem.register(registry)
        ActorSystem.start()

        try {
            block(registry)
        } finally {
            // Restore the shared global state so sibling test classes are not affected.
            ActorSystem.shutdown()
            ActorSystem.register(shared)
            ActorSystem.conf(ActorSystem.Conf())
        }
    }
}
