package io.smyrgeorge.actor4k.actor.types

import io.smyrgeorge.actor4k.actor.Actor
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import io.smyrgeorge.actor4k.actor.ActorRegistry

abstract class ManagedActor<C : Cmd, R : Reply> : Actor<C, R>() {

    init {
        // Register the actor to the registry.
        @Suppress("LeakingThis")
        ActorRegistry.register(this)
    }

}