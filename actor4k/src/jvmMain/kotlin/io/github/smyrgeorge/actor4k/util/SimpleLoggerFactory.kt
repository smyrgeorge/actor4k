package io.github.smyrgeorge.actor4k.util

import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SimpleLoggerFactory : Logger.Factory {
    actual override fun getLogger(clazz: KClass<*>): Logger =
        SimpleLogger(LoggerFactory.getLogger(clazz.java))
}