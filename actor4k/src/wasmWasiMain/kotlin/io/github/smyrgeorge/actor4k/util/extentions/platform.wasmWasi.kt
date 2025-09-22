package io.github.smyrgeorge.actor4k.util.extentions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

actual fun registerShutdownHook() {}
actual fun getEnv(key: String): String? = null
actual val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
actual fun KClass<*>.qualifiedOrSimpleName(): String =
    simpleName ?: error("Cannot determine qualified or simple name for $this.")
