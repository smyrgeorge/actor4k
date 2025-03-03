package io.github.smyrgeorge.actor4k.test.util

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.system.stats.SimpleStats
import io.github.smyrgeorge.actor4k.test.actor.AccountActor
import io.github.smyrgeorge.actor4k.test.actor.SlowProcessAccountActor
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory

object Registry {
    val registry = SimpleActorRegistry()
        .register(AccountActor::class) { AccountActor(it) }
        .register(SlowProcessAccountActor::class) { SlowProcessAccountActor(it)}

    init {
        ActorSystem
            .register(SimpleLoggerFactory())
            .register(SimpleStats())
            .register(registry)
    }
}