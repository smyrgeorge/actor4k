package io.github.smyrgeorge.actor4k.cluster.util.http

import io.github.smyrgeorge.actor4k.cluster.rpc.RpcReceiveService
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

object HttpServerUtils {
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