package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.KClass
import io.github.smyrgeorge.log4k.Logger as Log4kLogger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SimpleLoggerFactory : Logger.Factory {
    actual override fun getLogger(clazz: KClass<*>): Logger =
        SimpleLogger(Log4kLogger.factory.get(clazz))
}