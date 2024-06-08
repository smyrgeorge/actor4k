package io.github.smyrgeorge.actor4k.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend fun <A, B> Iterable<A>.mapParallel(context: CoroutineContext = Dispatchers.IO, f: suspend (A) -> B): List<B> =
    withContext(context) { map { async { f(it) } }.awaitAll() }

suspend fun <A> Iterable<A>.forEachParallel(context: CoroutineContext = Dispatchers.IO, f: suspend (A) -> Unit): Unit =
    withContext(context) { map { async { f(it) } }.awaitAll() }

fun <T> Iterable<T>.chunked(total: Int, chunks: Int): List<List<T>> =
    chunked(chunkSize(total, chunks))

private fun chunkSize(total: Int, chunks: Int): Int =
    if (chunks > total) chunks else total / chunks
