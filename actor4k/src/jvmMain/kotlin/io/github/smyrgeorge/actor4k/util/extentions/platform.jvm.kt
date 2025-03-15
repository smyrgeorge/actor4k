package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

actual fun registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(
        thread(start = false) {
            runBlocking {
                ActorSystem.shutdown()
            }
        }
    )
}

actual fun getEnv(key: String): String? = System.getenv(key)