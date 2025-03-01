package io.github.smyrgeorge.actor4k.jvm.util

import io.github.smyrgeorge.actor4k.util.Logger
import org.slf4j.Logger as Slf4jLogger

class JLogger(private val logger: Slf4jLogger) : Logger {
    override fun debug(msg: String): Unit = logger.debug(msg)
    override fun debug(msg: String, vararg args: Any): Unit = logger.debug(msg, *args)
    override fun info(msg: String): Unit = logger.info(msg)
    override fun info(msg: String, vararg args: Any): Unit = logger.info(msg, *args)
    override fun warn(msg: String): Unit = logger.warn(msg)
    override fun warn(msg: String, vararg args: Any): Unit = logger.warn(msg, *args)
    override fun error(msg: String): Unit = logger.error(msg)
    override fun error(msg: String, e: Throwable): Unit = logger.error(msg, e)
}