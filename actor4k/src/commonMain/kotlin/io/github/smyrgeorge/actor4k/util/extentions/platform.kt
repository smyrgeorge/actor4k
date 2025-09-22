package io.github.smyrgeorge.actor4k.util.extentions

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

expect fun registerShutdownHook()
expect fun getEnv(key: String): String?
expect val defaultDispatcher: CoroutineDispatcher
expect fun KClass<*>.qualifiedOrSimpleName(): String
