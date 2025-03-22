package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Request
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Response
import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Service responsible for handling RPC requests received via WebSocket frames.
 *
 * The service processes RPC requests, interacts with actors for the requested operations,
 * and returns appropriate responses. Utilizes a registry to manage actor instances and ensure
 * proper delegation of requests to the corresponding actors.
 *
 * @constructor Initializes the service with the necessary dependencies for processing requests.
 * @param loggerFactory Factory for creating logger instances used for logging activities.
 * @param protoBuf Serializer/deserializer for encoding and decoding RPC requests and responses.
 * @param registry Registry for managing actor instances and resolving actor addresses to their objects.
 */
@OptIn(ExperimentalSerializationApi::class)
class RpcReceiveService(
    loggerFactory: Logger.Factory,
    private val protoBuf: ProtoBuf,
    private val registry: ClusterActorRegistry
) {

    private val log: Logger = loggerFactory.getLogger(this::class)

    /**
     * Handles incoming WebSocket frames and processes RPC requests.
     *
     * @param session the active WebSocket session through which the frame is received and responses are sent.
     * @param frame the WebSocket frame containing the data to be processed. Only binary frames are supported.
     */
    fun receive(session: WebSocketSession, frame: Frame) {
        launch {
            if (frame !is Frame.Binary) {
                log.warn("Received non-binary frame: $frame")
                return@launch
            }
            val msg = protoBuf.decodeFromByteArray(Request.serializer(), frame.data)
            val res = when (msg) {
                is Request.Echo -> echo(msg)
                is Request.Tell -> tell(msg)
                is Request.Ask -> ask(msg)
                is Request.Status -> status(msg)
                is Request.Stats -> stats(msg)
                is Request.Shutdown -> shutdown(msg)
            }
            session.send(protoBuf.encodeToByteArray(Response.serializer(), res))
        }
    }

    /**
     * Processes an Echo request and generates a corresponding Echo response.
     *
     * @param msg the Echo request containing an id and a payload string.
     * @return the Echo response with the same id and the payload.
     */
    fun echo(msg: Request.Echo): Response.Echo = Response.Echo(msg.id, msg.payload)

    /**
     * Processes a Tell request by sending a message to a specified actor without awaiting a response.
     * Constructs a Response based on the outcome of the actor invocation.
     *
     * @param msg the Tell request containing the message ID, the address of the actor, and the payload to send.
     * @return an Empty response if the message is successfully sent to the actor,
     *         or a Failure response if an exception occurs during the process.
     */
    suspend fun tell(msg: Request.Tell): Response {
        return try {
            registry.get(msg.addr).tell(msg.payload)
            Response.Empty(msg.id)
        } catch (e: Exception) {
            Response.Failure(msg.id, e.message, e.cause?.message)
        }
    }

    /**
     * Processes an Ask request by sending a message to a specified actor and awaiting a response.
     * Constructs a Response based on the outcome of the actor invocation.
     *
     * @param msg the Ask request containing the message id, actor address, and payload to send.
     * @return a Success response if the actor processes the message successfully,
     *         or a Failure response if an error occurs or the message is not processed.
     */
    suspend fun ask(msg: Request.Ask): Response {
        val res = try {
            registry.get(msg.addr).ask<Actor.Message.Response>(msg.payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
        return if (res.isSuccess) Response.Success(msg.id, res.getOrThrow())
        else Response.Failure(msg.id, res.exceptionOrNull()?.message, res.exceptionOrNull()?.cause?.message)
    }

    /**
     * Processes a Status request to retrieve the current status of an actor associated with the specified address.
     *
     * @param msg the Status request containing the message ID and the address of the actor.
     * @return a Response.Status object containing the message ID and the actor's current status if successful,
     *         or a Response.Failure object if an error occurs during the process.
     */
    suspend fun status(msg: Request.Status): Response {
        return try {
            val status = registry.get(msg.addr).status()
            Response.Status(msg.id, status)
        } catch (e: Exception) {
            Response.Failure(msg.id, e.message, e.cause?.message)
        }
    }

    /**
     * Processes a Stats request to retrieve statistical data for the actor associated with the specified address.
     *
     * @param msg the Stats request containing the message ID and the address of the actor.
     * @return a Response.Stats object containing the message ID and the actor's statistics if successful,
     *         or a Response.Failure object if an error occurs during the process.
     */
    suspend fun stats(msg: Request.Stats): Response {
        return try {
            val stats = registry.get(msg.addr).stats()
            Response.Stats(msg.id, stats)
        } catch (e: Exception) {
            Response.Failure(msg.id, e.message, e.cause?.message)
        }
    }

    /**
     * Handles a shutdown request for an actor identified by its address.
     * Attempts to stop the associated actor and returns a response indicating the success or failure of the operation.
     *
     * @param msg The `Shutdown` request containing the unique message ID and the address of the actor to shut down.
     * @return A `Response.Empty` if the shutdown operation completes successfully, or a `Response.Failure` if any error occurs during the process.
     */
    suspend fun shutdown(msg: Request.Shutdown): Response {
        return try {
            registry.get(msg.addr).shutdown()
            Response.Empty(msg.id)
        } catch (e: Exception) {
            Response.Failure(msg.id, e.message, e.cause?.message)
        }
    }

    companion object {
        private object ClusterRpcReceiveServiceScope : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = EmptyCoroutineContext
        }

        private fun launch(f: suspend () -> Unit) {
            ClusterRpcReceiveServiceScope.launch(Dispatchers.Default) { f() }
        }
    }
}