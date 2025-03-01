package io.github.smyrgeorge.actor4k.java.util

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * An object responsible for registering and retrieving actor references within the system.
 * `JActorRegistry` provides utility methods to lookup or retrieve existing actor references
 * as `CompletableFuture` instances using the actor class, a key, and optionally a shard identifier.
 */
@Suppress("unused")
object JActorRegistry {
    fun <A : Actor> get(actor: Class<A>, key: String): CompletableFuture<ActorRef> =
        runBlocking { future { ActorSystem.get(actor.kotlin, key) } }
}
