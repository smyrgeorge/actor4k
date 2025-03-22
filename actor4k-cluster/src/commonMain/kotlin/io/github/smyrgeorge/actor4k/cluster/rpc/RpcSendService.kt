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
     * Sends an echo request with the provided message and awaits the corresponding echo response.
     *
     * @param msg The message payload to be sent as part of the echo request.
     * @return A response object of type [Response.Echo] containing the echoed message and request ID.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun echo(msg: String): Response.Echo {
        val req = Request.Echo(nextId(), msg)
        val res = rpc.request<Response.Echo>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    /**
     * Sends a message to a specified actor address and awaits a response.
     *
     * @param addr The target address of the actor to which the message should be sent.
     * @param msg The message to be sent to the target actor.
     * @return A [Response] object containing the acknowledgment or result from the actor.
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
     * Sends a message to a specified actor address and awaits a response.
     *
     * @param addr The target address of the actor to which the message should be sent.
     * @param msg The message to be sent to the target actor.
     * @return A [Response] object containing the result or acknowledgment from the actor.
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
     * Sends a request to retrieve the status of a specified actor and awaits the corresponding status response.
     *
     * @param addr The address of the actor whose status is to be retrieved.
     * @return A [Response.Status] object containing the status information of the specified actor.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun status(addr: Address): Response.Status {
        val req = Request.Status(nextId(), addr)
        val res = rpc.request<Response.Status>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    /**
     * Retrieves statistics of the specified actor by sending a `Stats` request to the target actor address
     * and awaiting the corresponding `Stats` response.
     *
     * @param addr The address of the actor whose statistics are to be retrieved.
     * @return A [Response.Stats] object containing statistical information about the specified actor.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun stats(addr: Address): Response.Stats {
        val req = Request.Stats(nextId(), addr)
        val res = rpc.request<Response.Stats>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    /**
     * Sends a shutdown request to the specified actor address and waits for an acknowledgment response.
     *
     * @param addr The address of the actor to be shut down.
     * @throws IllegalStateException if the response ID does not match the request ID.
     */
    suspend fun shutdown(addr: Address) {
        val req = Request.Shutdown(nextId(), addr)
        val res = rpc.request<Response.Empty>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
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

    private fun Request.serialize(): ByteArray =
        protoBuf.encodeToByteArray(Request.serializer(), this)

    companion object {
        private lateinit var protoBuf: ProtoBuf
        private var idx: Long = 0
        private val idxMutex = Mutex()
        private suspend fun nextId(): Long = idxMutex.withLock { idx++ }
        private val rpc = RpcManager<Response>(ActorSystem.loggerFactory, 30.seconds)

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