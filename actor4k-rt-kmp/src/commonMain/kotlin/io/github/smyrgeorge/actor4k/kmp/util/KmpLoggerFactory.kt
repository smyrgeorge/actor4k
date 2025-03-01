package io.github.smyrgeorge.actor4k.kmp.util

import io.github.smyrgeorge.actor4k.util.Logger
import kotlin.reflect.KClass
import io.github.smyrgeorge.log4k.Logger as Log4kLogger

class KmpLoggerFactory : Logger.Factory {
    override fun getLogger(clazz: KClass<*>): Logger =
        KmpLogger(Log4kLogger.factory.get(clazz))
}