package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.staticCFunction
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