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
     * Processes a Tell request, sending a message to the specified actor.
     *
     * Handles errors during the process and returns a corresponding response.
     *
     * @param msg the Tell request containing the message id, actor address, and payload to send.
     * @return an Empty response if the operation succeeds, or a Failure response if an error occurs.
     */
    suspend fun tell(msg: Request.Tell): Response {
        return try {
            registry.get(msg.addr).tell(msg.payload)
            Response.Empty(msg.id)
        } catch (ex: Throwable) {
            Response.Failure(msg.id, ex.message ?: "", ex.cause?.message)
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
        val res = registry.get(msg.addr).ask<Actor.Message.Response>(msg.payload)
        return when {
            res.isSuccess -> Response.Success(msg.id, res.getOrThrow())
            else -> Response.Failure(
                id = msg.id,
                message = res.exceptionOrNull()?.message ?: "",
                cause = res.exceptionOrNull()?.cause?.message
            )
        }
    }

    /**
     * Retrieves the current status of an actor associated with the provided address in the request.
     *
     * @param msg the Status request containing the message id and the address of the actor.
     * @return a Response.Status object containing the message id and the current status of the actor.
     */
    suspend fun status(msg: Request.Status): Response.Status {
        val res = registry.get(msg.addr).status()
        return Response.Status(msg.id, res)
    }

    /**
     * Processes a Stats request and retrieves statistical information about the actor
     * associated with the specified address.
     *
     * @param msg the Stats request containing the message id and the address of the actor.
     * @return a Response.Stats object containing the message id and the statistical data for the actor.
     */
    suspend fun stats(msg: Request.Stats): Response.Stats {
        val res = registry.get(msg.addr).stats()
        return Response.Stats(msg.id, res)
    }

    /**
     * Processes a Shutdown request by shutting down the actor associated with the specified address.
     *
     * This method retrieves the actor instance linked to the provided address from the registry
     * and initiates its shutdown process. The method ensures the actor transitions to a state
     * where it ceases operations and releases its allocated resources.
     *
     * @param msg the Shutdown request containing the message id and the address of the actor to shut down.
     * @return an Empty response indicating the shutdown operation was initiated.
     */
    suspend fun shutdown(msg: Request.Shutdown): Response.Empty {
        registry.get(msg.addr).shutdown()
        return Response.Empty(msg.id)
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