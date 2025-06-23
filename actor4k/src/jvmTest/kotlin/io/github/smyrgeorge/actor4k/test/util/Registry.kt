package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.ErrorThrowingAccountActor
import io.github.smyrgeorge.actor4k.test.actor.InitMethodFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.LongOnShutdownActor
import io.github.smyrgeorge.actor4k.test.actor.OnBeforeActivateFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.ResourceHoldingActor
import io.github.smyrgeorge.actor4k.test.actor.ShortLivedActor
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateWithErrorInActivationAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowInitAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessingAccountActor
import io.github.smyrgeorge.actor4k.test.actor.StashingActor
import io.github.smyrgeorge.actor4k.test.actor.ThrowingDuringMessageProcessingActor
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory

object Registry {
    val loggerFactory = SimpleLoggerFactory()

    val registry = SimpleActorRegistry(loggerFactory)
        .factoryFor(AccountActor::class) { AccountActor(it) }
        .factoryFor(ErrorThrowingAccountActor::class) { ErrorThrowingAccountActor(it) }
        .factoryFor(InitMethodFailsAccountActor::class) { InitMethodFailsAccountActor(it) }
        .factoryFor(LongOnShutdownActor::class) { LongOnShutdownActor(it) }
        .factoryFor(OnBeforeActivateFailsAccountActor::class) { OnBeforeActivateFailsAccountActor(it) }
        .factoryFor(ResourceHoldingActor::class) { ResourceHoldingActor(it) }
        .factoryFor(ShortLivedActor::class) { ShortLivedActor(it) }
        .factoryFor(SlowActivateAccountActor::class) { SlowActivateAccountActor(it) }
        .factoryFor(SlowInitAccountActor::class) { SlowInitAccountActor(it) }
        .factoryFor(SlowProcessingAccountActor::class) { SlowProcessingAccountActor(it) }
        .factoryFor(SlowActivateWithErrorInActivationAccountActor::class) {
            SlowActivateWithErrorInActivationAccountActor(it)
        }
        .factoryFor(ThrowingDuringMessageProcessingActor::class) { ThrowingDuringMessageProcessingActor(it) }
        .factoryFor(StashingActor::class) { StashingActor(it) }

    init {
        ActorSystem
            .register(loggerFactory)
            .register(registry)
    }
}
