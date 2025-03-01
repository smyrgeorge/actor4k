package io.github.smyrgeorge.actor4k.util

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SimpleLogger : Logger {
    override fun debug(msg: String)
    override fun debug(msg: String, vararg args: Any)
    override fun info(msg: String)
    override fun info(msg: String, vararg args: Any)
    override fun warn(msg: String)
    override fun warn(msg: String, vararg args: Any)
    override fun error(msg: String)
    override fun error(msg: String, e: Throwable)
}