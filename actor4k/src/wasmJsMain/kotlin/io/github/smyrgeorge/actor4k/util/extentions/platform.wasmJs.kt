package io.github.smyrgeorge.actor4k.util.extentions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun registerShutdownHook() {}
actual fun getEnv(key: String): String? = null
actual val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default