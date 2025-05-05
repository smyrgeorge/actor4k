package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

actual fun registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(
        thread(name = "shutdown", start = false) {
            runBlocking { ActorSystem.shutdown() }
        }
    )
}

actual fun getEnv(key: String): String? = System.getenv(key)

/**
 * Provides an [ExecutorCoroutineDispatcher] using a virtual thread per task executor.
 * This dispatcher is optimized for IO-bound operations and can handle numerous concurrent
 * blocking tasks efficiently without the limitations of a traditional fixed-size thread pool.
 * Suitable for tasks that block on IO operations such as file or network access.
 */
private val LOOM: ExecutorCoroutineDispatcher =
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

actual val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
@Suppress("unused") val loomDispatcher: CoroutineDispatcher = LOOM