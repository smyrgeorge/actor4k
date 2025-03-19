package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor.Protocol
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessAccountActor
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ActorGracefulShutdownTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Actor should graceful shutdown`(): Unit = runBlocking {
        val ref: ActorRef = ActorSystem.get(SlowProcessAccountActor::class, ACC0000)
        assertThat(registry.size()).isEqualTo(1)
        repeat(2) { ref.tell(Protocol.Req("Ping!")) }
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        delay(1000)
        actor.shutdown()
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUTTING_DOWN)
        delay(1500)
        assertThat(actor.status()).isEqualTo(Actor.Status.SHUT_DOWN)
        assertThat(actor.stats().shutDownAt).isNotNull()
        assertThat(registry.size()).isZero()
        assertThat(actor.stats().receivedMessages).isEqualTo(2)
    }

    companion object {
        private const val ACC0000: String = "ACC0000"
    }
}