package io.github.smyrgeorge.actor4k.util.java

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

object JActorRegistry {
    fun <A : Actor> get(
        actor: Class<A>,
        key: String,
        shard: String = key
    ): CompletableFuture<Actor.Ref> = runBlocking { future { ActorSystem.get(actor, key, shard) } }

    fun <A : Actor> get(
        actor: Class<A>,
        key: String
    ): CompletableFuture<Actor.Ref> = runBlocking { future { ActorSystem.get(actor, key, key) } }
}
