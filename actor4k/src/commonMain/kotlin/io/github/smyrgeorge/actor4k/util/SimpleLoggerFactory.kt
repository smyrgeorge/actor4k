package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.KClass

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SimpleLoggerFactory() : Logger.Factory {
    override fun getLogger(clazz: KClass<*>): Logger
}