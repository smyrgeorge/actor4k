package io.github.smyrgeorge.actor4k.cluster.util.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlin.time.Duration.Companion.seconds

object HttpClientUtils {
    /**
     * Creates and configures an instance of [HttpClient] using the CIO engine with WebSocket support.
     *
     * The client is designed to enable WebSocket communication with a specified ping interval of 15 seconds.
     *
     * @return An instance of [HttpClient] configured with CIO as the engine and WebSocket functionality installed.
     */
    fun create(): HttpClient =
        HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 15.seconds
            }
        }
}