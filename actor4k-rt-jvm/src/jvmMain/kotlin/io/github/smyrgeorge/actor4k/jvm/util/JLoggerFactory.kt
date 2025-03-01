package io.github.smyrgeorge.actor4k.jvm.util

import io.github.smyrgeorge.actor4k.util.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class JLoggerFactory : Logger.Factory {
    override fun getLogger(clazz: KClass<*>): Logger =
        JLogger(LoggerFactory.getLogger(clazz.java))
}