package io.github.smyrgeorge.actor4k.test.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend inline fun <A> Iterable<A>.forEachParallel(
    context: CoroutineContext = Dispatchers.IO,
    crossinline f: suspend (A) -> Unit
): Unit = withContext(context) { map { async { f(it) } }.awaitAll() }