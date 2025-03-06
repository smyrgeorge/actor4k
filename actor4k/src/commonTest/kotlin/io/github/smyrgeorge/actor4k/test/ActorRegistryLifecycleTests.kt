package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.InitMethodFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.NotRegisteredAccountActor
import io.github.smyrgeorge.actor4k.test.actor.OnBeforeActivateFailsAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.test.util.forEachParallel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFails

class ActorRegistryLifecycleTests {
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
    fun `If init fails the registry get method should also fail`(): Unit = runBlocking {
        assertFails {
            registry.get(InitMethodFailsAccountActor::class, ACC0000)
        }
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `If onBeforeActivate fails the registry get method should also fail`(): Unit = runBlocking {
        assertFails {
            registry.get(OnBeforeActivateFailsAccountActor::class, ACC0000)
        }
        assertThat(registry.size()).isZero()
    }

    @Test
    fun `Concurrent gets for the same actor should not create duplicates`(): Unit = runBlocking {
        val workers = listOf(1, 2, 3, 4)
        workers.forEachParallel {
            repeat(100) {
                val ref = registry.get(AccountActor::class, ACC0001)
                ref.tell(AccountActor.Req("Ping!"))
            }
        }

        assertThat(registry.size()).isEqualTo(1)
        val ref = registry.get(AccountActor::class, ACC0001)
        val actor = registry.get(ref as LocalRef)
        assertThat(actor.key).isEqualTo(ACC0001)
        delay(1000) // Ensure that all messages are processed.
        assertThat(actor.stats().processedMessages).isEqualTo(4 * 100)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
        private const val ACC0001: String = "ACC0001"
    }
}