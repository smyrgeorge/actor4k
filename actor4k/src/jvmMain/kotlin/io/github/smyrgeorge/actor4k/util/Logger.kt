package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.KClass

/**
 * Interface for a logger utility, facilitating structured logging at various levels.
 *
 * Provides methods for logging messages at different severity levels,
 * such as debug, info, warning, and error. Supports optional argument
 * substitution in log messages and error handling for error logs.
 */
interface Logger {
    fun debug(msg: String)
    fun debug(msg: String, vararg args: Any)
    fun info(msg: String)
    fun info(msg: String, vararg args: Any)
    fun warn(msg: String)
    fun warn(msg: String, vararg args: Any)
    fun error(msg: String)
    fun error(msg: String, e: Throwable)

    /**
     * Factory interface responsible for creating instances of `Logger` for a given class.
     *
     * Typically used to provide logging capabilities specific to a class, enabling
     * structured logging and facilitating monitoring or debugging processes.
     *
     * Implementors of this interface must define how `Logger` instances are created or
     * retrieved, and may encapsulate additional configuration or logging framework integration.
     */
    interface Factory {
        fun getLogger(clazz: KClass<*>): Logger
    }
}