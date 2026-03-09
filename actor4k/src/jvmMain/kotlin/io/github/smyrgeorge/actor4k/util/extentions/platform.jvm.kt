package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import kotlin.reflect.KClass

actual fun registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(
        thread(name = "shutdown", start = false) {
            runBlocking { ActorSystem.shutdown() }
        }
    )
}

actual fun getEnv(key: String): String? = System.getenv(key)
actual val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
actual fun KClass<*>.qualifiedOrSimpleName(): String =
    qualifiedName ?: simpleName ?: error("Cannot determine qualified or simple name for $this.")
