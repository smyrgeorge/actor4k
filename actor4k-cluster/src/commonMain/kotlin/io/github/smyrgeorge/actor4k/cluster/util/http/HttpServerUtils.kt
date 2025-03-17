package io.github.smyrgeorge.actor4k.cluster.util.http

import io.github.smyrgeorge.actor4k.cluster.rpc.RpcReceiveService
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

object HttpServerUtils {
    /**
     * Creates an embedded server with WebSocket functionality, routing configuration, and RPC message handling.
     *
     * @param port The port on which the server will listen for incoming connections.
     * @param routing A lambda function to define additional routing configurations for the server.
     * @param receive The service responsible for processing WebSocket frames and handling RPC requests.
     * @return An embedded server instance configured with CIO engine, WebSocket support, and custom routing logic.
     */
    fun create(
        port: Int,
        routing: Routing.() -> Unit = {},
        receive: RpcReceiveService
    ): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(CIO, port) {
            install(WebSockets) {
                timeout = 60.seconds
                pingPeriod = 15.seconds
            }

            routing {
                routing(this)
                webSocket("/cluster") {
                    log.info("Incoming connection from ${call.request.local.remoteAddress}")
                    try {
                        for (frame in incoming) {
                            receive.receive(this, frame)
                        }
                    } catch (e: Exception) {
                        log.error("WebSocket connection error: ${e.message}")
                    }
                }
            }
        }
}