package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.launch
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Maintains a WebSocket connection to a remote node and provides mechanisms for sending and
 * handling communication in a clustered environment.
 *
 * This class is responsible for establishing a WebSocket session with a specified `ClusterNode`
 * using the provided HTTP client. It includes automatic reconnection logic in case of disconnection
 * and ensures reliable message delivery by retrying operations when failures occur.
 *
 * @constructor Initializes the WebSocket session for the given `ClusterNode`. The connection
 * is established asynchronously upon instantiation.
 *
 * @param loggerFactory Factory for creating a logger instance for this class to enable logging.
 * @param client HTTP client used to manage the WebSocket connections.
 * @param node The cluster node representing the target WebSocket endpoint.
 */
class RpcWebSocketSession(
    loggerFactory: Logger.Factory,
    private val client: HttpClient,
    internal val node: ClusterNode
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

    /**
     * Sends a binary payload to the connected WebSocket session with retry logic.
     *
     * @param payload The binary data to be sent as a ByteArray.
     * Throws an exception if the session is permanently closed or reaches maximum retry attempts.
     */
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

    /**
     * Closes the current WebSocket session and marks the session as closed.
     *
     * This method sets the internal state of the session to closed and performs the
     * necessary cleanup by closing the active WebSocket session, if any.
     * Once called, no further communication through this session is possible.
     *
     * Throws no exceptions and ensures safe cleanup of resources.
     */
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
}