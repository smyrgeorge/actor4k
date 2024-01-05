package io.github.smyrgeorge.actor4k.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

suspend fun <T> io(f: suspend () -> T): T =
    withContext(Dispatchers.IO) { f() }

suspend fun <T> retry(
    times: Int = 3,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000,    // 1 second
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    return withContext(Dispatchers.IO) {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return@withContext block()
            } catch (e: Exception) {
                // you can log an error here and/or make a more finer-grained
                // analysis of the cause to see if retry is needed
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return@withContext block() // last attempt
    }
}

fun <T> retryBlocking(
    times: Int = 3,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000,    // 1 second
    factor: Double = 2.0,
    block: suspend () -> T
): T = runBlocking { retry(times, initialDelay, maxDelay, factor, block) }