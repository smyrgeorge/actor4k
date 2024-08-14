package io.github.smyrgeorge.actor4k.util.java

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

data class JRef(val ref: ActorRef) {
    fun tell(msg: Any): CompletableFuture<Unit> =
        runBlocking { future { ref.tell(msg) } }

    fun <R> ask(msg: Any): CompletableFuture<R> =
        runBlocking { future { ref.ask(msg) } }
}
