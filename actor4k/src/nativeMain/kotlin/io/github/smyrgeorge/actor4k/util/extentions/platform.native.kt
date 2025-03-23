package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import platform.posix.SIGINT
import platform.posix.atexit
import platform.posix.signal

@OptIn(ExperimentalForeignApi::class)
actual fun registerShutdownHook() {
    atexit(staticCFunction<Unit> {
        runBlocking {
            ActorSystem.shutdown()
        }
    })
    signal(SIGINT, staticCFunction<Int, Unit> {
        runBlocking {
            ActorSystem.shutdown()
        }
    })
}

@OptIn(ExperimentalForeignApi::class)
actual fun getEnv(key: String): String? = platform.posix.getenv(key)?.toKString()
actual val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default