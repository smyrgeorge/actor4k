package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.KClass

interface Logger {
    fun debug(msg: String)
    fun debug(msg: String, vararg args: Any)
    fun info(msg: String)
    fun info(msg: String, vararg args: Any)
    fun warn(msg: String)
    fun warn(msg: String, vararg args: Any)
    fun error(msg: String)
    fun error(msg: String, e: Throwable)

    interface Factory {
        fun getLogger(clazz: KClass<*>): Logger
    }
}