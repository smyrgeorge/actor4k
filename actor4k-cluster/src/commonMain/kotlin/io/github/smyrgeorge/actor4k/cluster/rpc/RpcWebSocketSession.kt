package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.launch
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

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

    private val retryConnectMillis = 200L
    private val retryConnectMaxMillis = 5_000L
    private val retrySendMillis = 100L
    private val retrySendMaxAttempts = 10

    @Volatile
    private var closed = false

    @Volatile
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
            if (closed) error("Session permanently closed. Cannot send message to $node")
            delay((retrySendMillis * (retryCount + 1)).milliseconds)
            retryCount++
            if (retryCount >= retrySendMaxAttempts) error("Connection to $node lost.")
        }
        session?.send(Frame.Binary(true, payload))
    }

    /**
     * Closes the current WebSocket session and marks the session as closed.
     *
     * This method sets the internal state of the session to closed and performs the
     * necessary cleanup by closing the active WebSocket session, if any. Marking the
     * session as closed also stops the reconnection loop.
     * Once called, no further communication through this session is possible.
     *
     * Throws no exceptions and ensures safe cleanup of resources.
     */
    suspend fun close() {
        closed = true
        session?.close()
    }

    /**
     * Establishes and maintains the WebSocket connection, reconnecting on any disconnection.
     *
     * A single loop owns the connection lifecycle: it connects, processes incoming frames until
     * the connection ends (peer close, error, or [close]), then reconnects with a bounded,
     * incremental backoff. The loop terminates only once [close] has been called.
     */
    private suspend fun create() {
        var retryCount = 0
        while (!closed) {
            try {
                client.webSocket("ws://${node.address}/cluster") {
                    log.info("Connection established with $node")
                    session = this
                    retryCount = 0 // Reset the backoff after a successful connection.
                    try {
                        for (e in incoming) {
                            if (e !is Frame.Binary) continue
                            runCatching { RpcSendService.rpcHandleResponse(e.data) }
                                .onFailure { log.warn("Failed to handle response from $node (${it.message})") }
                        }
                    } finally {
                        // The connection ended (peer closed, error, or close() was called).
                        // Clear the session so send() waits for a fresh one instead of using a dead one.
                        session = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Connection failed for $node (${e.message ?: ""}), retrying...")
            }
            if (closed) break
            val backoff = (retryConnectMillis * (retryCount + 1)).coerceAtMost(retryConnectMaxMillis)
            delay(backoff.milliseconds)
            retryCount++
        }
    }
}
