package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.InitMethodFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.OnBeforeActivateFailsAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowActivateWithErrorInActivationAccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessAccountActor
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory

object Registry {
    val loggerFactory = SimpleLoggerFactory()

    val registry = SimpleActorRegistry(loggerFactory)
        .factoryFor(AccountActor::class) { AccountActor(it) }
        .factoryFor(InitMethodFailsAccountActor::class) { InitMethodFailsAccountActor(it) }
        .factoryFor(OnBeforeActivateFailsAccountActor::class) { OnBeforeActivateFailsAccountActor(it) }
        .factoryFor(SlowActivateAccountActor::class) { SlowActivateAccountActor(it) }
        .factoryFor(SlowProcessAccountActor::class) { SlowProcessAccountActor(it) }
        .factoryFor(SlowActivateWithErrorInActivationAccountActor::class) {
            SlowActivateWithErrorInActivationAccountActor(it)
        }

    init {
        ActorSystem
            .register(loggerFactory)
            .register(registry)
    }
}