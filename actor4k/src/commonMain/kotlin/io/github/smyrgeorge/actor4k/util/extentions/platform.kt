package io.github.smyrgeorge.actor4k.util.extentions

import kotlinx.coroutines.CoroutineDispatcher

expect fun registerShutdownHook()
expect fun getEnv(key: String): String?
expect val defaultDispatcher: CoroutineDispatcher