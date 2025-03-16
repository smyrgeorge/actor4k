package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class RpcWebSocketSession(
    loggerFactory: Logger.Factory,
    private val client: HttpClient,
    private val node: ClusterNode
) {
    private val log: Logger = loggerFactory.getLogger(this::class)

    private val address: String = node.address
    private var closed = false
    private val retryConnectMillis = 200L
    private val retrySendMillis = 100L
    private val retrySendMaxAttempts = 10
    private var session: DefaultClientWebSocketSession? = null

    init {
        launch { create() }
    }

    suspend fun send(payload: ByteArray) {
        if (closed) error("Session permanently closed. Cannot send message to $node")
        var retryCount = 0
        while (session == null) {
            delay(retrySendMillis * (retryCount + 1))
            retryCount++
            if (retryCount >= retrySendMaxAttempts) error("Connection to $node lost.")
        }
        session?.send(Frame.Binary(true, payload))
    }

    suspend fun close() {
        closed = true
        session?.close()
    }

    private suspend fun create() {
        if (closed) return

        session = null
        var retryCount = 0
        while (session == null) {
            try {
                client.webSocket("ws://$address/cluster") {
                    log.info("Connection established with $node")
                    session = this
                    try {
                        for (e in incoming) {
                            if (e !is Frame.Binary) continue
                            runCatching { RpcSendService.rpcHandleResponse(e.data) }
                        }
                    } catch (e: Exception) {
                        log.error("Connection error for $node (${e.message}), reopening...")
                        // Reopen session
                        launch { create() }
                    }
                }
            } catch (e: Exception) {
                log.warn("Connection failed for $node (${e.message ?: ""}), retrying...")
            }
            delay(retryConnectMillis * (retryCount + 1))
            retryCount++
        }
    }

    companion object {
        private object WebSocketSessionScope : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = EmptyCoroutineContext
        }

        private fun launch(f: suspend () -> Unit) {
            WebSocketSessionScope.launch(Dispatchers.Default) { f() }
        }
    }
}