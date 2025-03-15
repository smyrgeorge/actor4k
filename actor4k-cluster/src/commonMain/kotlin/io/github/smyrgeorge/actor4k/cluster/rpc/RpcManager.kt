package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.util.collections.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RpcManager<R>(
    loggerFactory: Logger.Factory,
    private val timeout: Duration
) {
    val log: Logger = loggerFactory.getLogger(this::class)

    // Map that keeps in track the active (waiting response) channels.
    private val channels = ConcurrentMap<Long, Channel<R>>(1_000)

    private fun open(reqId: Long): Channel<R> {
        // Create channel.
        val channel = Channel<R>()
        // Save the channel to the [channels] Map.
        channels[reqId] = channel
        // Return the channel to the client.
        return channel
    }

    suspend fun <T> request(reqId: Long, f: suspend () -> Unit): T {
        // Open channel here.
        val channel: Channel<R> = open(reqId)

        return try {
            val res = withTimeout(timeout) {
                f()
                // Wait (suspend) the response here.
                channel.receive()
            }
            // Return the response to the client.
            @Suppress("UNCHECKED_CAST")
            res as? T ?: error("Cannot cast to given type.")
        } catch (e: TimeoutCancellationException) {
            cancel(reqId, channel, e)
            throw e
        } catch (e: Exception) {
            cancel(reqId, channel, null)
            throw e
        } finally {
            // Close the channel.
            close(reqId)
        }
    }

    /**
     * Finds the corresponding [Channel] and send the [Result]
     * Ignores messages for unknown (or missing) [Channel]s.
     */
    suspend fun response(reqId: Long, res: R) {
        try {
            withTimeout(2.seconds) {
                // Send the response to all waiting clients.
                channels[reqId]?.send(res)
            }
        } catch (e: Exception) {
            log.warn("[$reqId] Could not send response (${e.message})")
            close(reqId)
        }
    }

    private fun cancel(reqId: Long, channel: Channel<R>, e: TimeoutCancellationException?) {
        channels.remove(reqId)
        channel.cancel(e)
    }

    private fun close(reqId: Long) {
        channels[reqId]?.let {
            // Close the channel.
            it.close()
            // Remove it from the channels map.
            channels.remove(reqId)
        }
    }
}
