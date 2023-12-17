package io.github.smyrgeorge.actor4k.actor.types

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.system.ActorRegistry

abstract class ManagedActor<C : Cmd, R : Reply> : Actor<C, R>() {

    init {
        // Register the actor to the registry.
        @Suppress("LeakingThis")
        ActorRegistry.register(this)
    }

}