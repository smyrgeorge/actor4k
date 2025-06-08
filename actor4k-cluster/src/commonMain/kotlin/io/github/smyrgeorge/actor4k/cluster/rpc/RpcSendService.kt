package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Request
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Response
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.time.Duration.Companion.seconds

/**
 * Service class responsible for handling RPC (Remote Procedure Call) communication over a WebSocket session.
 *
 * This class provides methods for sending various types of requests and handling the responses in a
 * clustered system. The communication is conducted via serialization and deserialization of messages
 * using Kotlin serialization with ProtoBuf format.
 *
 * @constructor Creates an instance of `RpcSendService` with the specified logger factory, serialization
 * mechanism (ProtoBuf), and WebSocket session.
 *
 * @param loggerFactory Factory for creating logger instances for this service to enable structured
 * logging during communication and debugging.
 * @param protoBuf The ProtoBuf serializer used for encoding and decoding messages.
 * @param session The WebSocket session over which the RPC requests and responses are transmitted.
 *
 * This class includes support for the following operations:
 *
 * - Sending an echo message.
 * - Sending fire-and-forget messages (tell).
 * - Sending requests with expected responses (ask).
 * - Querying the status and statistics of a remote actor.
 * - Initiating the shutdown of a remote actor.
 * - Closing the WebSocket session when communication is complete.
 *
 * The service ensures reliability and data consistency by validating response IDs with the request IDs
 * during each interaction. Any inconsistency raises an error, ensuring the integrity of the communication process.
 *
 * Additional functionality is provided via a companion object:
 *
 * - Managing a shared RPC message dispatcher that associates responses to their corresponding requests.
 * - Ensuring unique message IDs for requests by maintaining an internal counter with safe concurrent access.
 * - Handling incoming responses sent from the remote side by deserializing and forwarding them to the RPC manager.
 */
@OptIn(ExperimentalSerializationApi::class)
class RpcSendService(
    loggerFactory: Logger.Factory,
    private val protoBuf: ProtoBuf,
    internal val session: RpcWebSocketSession
) {

    @Suppress("unused")
    private val log: Logger = loggerFactory.getLogger(this::class)

    init {
        RpcSendService.protoBuf = protoBuf
    }

    /**
     * Sends an echo request with a given message and awaits a response.
     *
     * @param msg The message to be sent as part of the echo request.
     * @return A [Result] containing the [Response.Echo] received in response, or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun echo(msg: String): Result<Response.Echo> = runCatching {
        val req = Request.Echo(nextId(), msg)
        val res = rpc.request<Response.Echo>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Sends a message to a specified actor address and waits for a response.
     *
     * @param addr The target address of the actor to which the message should be sent.
     * @param msg The message to be delivered to the target actor.
     * @return A [Result] containing the [Response] received in reply, or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun tell(addr: Address, msg: Actor.Message): Result<Response> = runCatching {
        val req = Request.Tell(nextId(), addr, msg)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Sends a message to a specified actor address and waits for a response,
     * ensuring the response corresponds to the sent request.
     *
     * @param addr The address of the target actor to which the message should be sent.
     * @param msg The message to deliver to the target actor.
     * @return A [Result] containing the [Response] received in reply, or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun ask(addr: Address, msg: Actor.Message): Result<Response> = runCatching {
        val req = Request.Ask(nextId(), addr, msg)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Sends a status request to the specified actor address and awaits the corresponding response.
     *
     * @param addr The address of the actor for which the status is being requested.
     * @return A [Result] containing the [Response] (typically a [Response.Status]) received in response, or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun status(addr: Address): Result<Response> = runCatching {
        val req = Request.Status(nextId(), addr)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Sends a stats request to the specified actor address and waits for the corresponding response.
     *
     * @param addr The address of the actor for which the stats are being requested.
     * @return A [Result] containing the [Response] (typically a [Response.Stats]) received in response, or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun stats(addr: Address): Result<Response> = runCatching {
        val req = Request.Stats(nextId(), addr)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Sends a shutdown request to the specified actor address and waits for the response.
     *
     * @param addr The address of the actor to be shut down.
     * @return A [Result] containing the [Response] received in response to the shutdown request,
     * or an error if the operation fails.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun shutdown(addr: Address): Result<Response> = runCatching {
        val req = Request.Shutdown(nextId(), addr)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        res
    }

    /**
     * Closes the current session associated with the `RpcSendService`.
     *
     * This method finalizes and safely terminates the session, ensuring
     * that resources tied to it are properly cleaned up. After invoking this method,
     * further communication through the session is no longer possible.
     *
     * @return A [Unit] indicating the completion of the close operation.
     */
    suspend fun close(): Unit = session.close()

    /**
     * Serializes the current [Request] instance into a [ByteArray].
     *
     * This method uses the provided protobuf serialization mechanism to convert
     * the [Request] into a compact byte array representation suitable for transmission
     * or storage.
     *
     * @return A [ByteArray] containing the serialized representation of the [Request].
     */
    private fun Request.serialize(): ByteArray = protoBuf.encodeToByteArray(Request.serializer(), this)

    companion object {
        private lateinit var protoBuf: ProtoBuf
        private var idx: Long = 0
        private val idxMutex = Mutex()
        private suspend fun nextId(): Long = idxMutex.withLock { idx++ }
        private val rpc = RpcManager<Response>(ActorSystem.loggerFactory, 120.seconds)

        /**
         * Handles the incoming response by decoding it from a byte array and passing it to the appropriate request handler.
         *
         * @param bytes The serialized representation of the response message as a byte array.
         */
        suspend fun rpcHandleResponse(bytes: ByteArray) {
            val res = protoBuf.decodeFromByteArray(Response.serializer(), bytes)
            rpc.response(res.id, res)
        }
    }
}