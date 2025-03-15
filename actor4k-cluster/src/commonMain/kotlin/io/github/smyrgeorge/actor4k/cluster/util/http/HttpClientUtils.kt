package io.github.smyrgeorge.actor4k.cluster.util.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlin.time.Duration.Companion.seconds

object HttpClientUtils {
    fun create(): HttpClient =
        HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 15.seconds
            }
        }
}