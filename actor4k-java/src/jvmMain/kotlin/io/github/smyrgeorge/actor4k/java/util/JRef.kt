package io.github.smyrgeorge.actor4k.java.util

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * A wrapper class for `ActorRef` to provide Java interoperability.
 *
 * This class allows interaction with an actor reference using `CompletableFuture` methods,
 * making it suitable for usage in Java-based codebases.
 *
 * @property ref The actor reference encapsulated by this wrapper.
 */
@Suppress("unused")
data class JRef(val ref: ActorRef) {
    fun tell(msg: Any): CompletableFuture<Unit> =
        runBlocking { future { ref.tell(msg) } }

    fun <R> ask(msg: Any): CompletableFuture<R> =
        runBlocking { future { ref.ask(msg) } }
}
