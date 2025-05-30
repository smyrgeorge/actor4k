package io.github.smyrgeorge.actor4k.util

import org.slf4j.Logger as Slf4jLogger

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SimpleLogger(
    val logger: Slf4jLogger
) : Logger {
    actual override fun debug(msg: String): Unit = logger.debug(msg)
    actual override fun debug(msg: String, vararg args: Any): Unit = logger.debug(msg, *args)
    actual override fun info(msg: String): Unit = logger.info(msg)
    actual override fun info(msg: String, vararg args: Any): Unit = logger.info(msg, *args)
    actual override fun warn(msg: String): Unit = logger.warn(msg)
    actual override fun warn(msg: String, vararg args: Any): Unit = logger.warn(msg, *args)
    actual override fun error(msg: String): Unit = logger.error(msg)
    actual override fun error(msg: String, e: Throwable): Unit = logger.error(msg, e)
}