package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.*
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory

object Registry {
    val loggerFactory = SimpleLoggerFactory()

    val registry = SimpleActorRegistry(loggerFactory)
        .factoryFor(AccountActor::class) { AccountActor(it) }
        .factoryFor(ErrorThrowingAccountActor::class) { ErrorThrowingAccountActor(it) }
        .factoryFor(InitMethodFailsAccountActor::class) { InitMethodFailsAccountActor(it) }
        .factoryFor(LongOnShutdownActor::class) { LongOnShutdownActor(it) }
        .factoryFor(OnBeforeActivateFailsAccountActor::class) { OnBeforeActivateFailsAccountActor(it) }
        .factoryFor(ResourceHoldingAccountActor::class) { ResourceHoldingAccountActor(it) }
        .factoryFor(ShortLivedAccountActor::class) { ShortLivedAccountActor(it) }
        .factoryFor(SlowActivateAccountActor::class) { SlowActivateAccountActor(it) }
        .factoryFor(SlowInitAccountActor::class) { SlowInitAccountActor(it) }
        .factoryFor(SlowProcessingAccountActor::class) { SlowProcessingAccountActor(it) }
        .factoryFor(SlowActivateWithErrorInActivationAccountActor::class) {
            SlowActivateWithErrorInActivationAccountActor(it)
        }
        .factoryFor(ThrowingDuringMessageProcessingAccountActor::class) { ThrowingDuringMessageProcessingAccountActor(it) }
        .factoryFor(StashingActor::class) { StashingActor(it) }
        .factoryFor(TerminatingAccountActor::class) { TerminatingAccountActor(it) }

    init {
        ActorSystem
            .register(loggerFactory)
            .register(registry)
    }
}
