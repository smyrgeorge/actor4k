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

@OptIn(ExperimentalSerializationApi::class)
class RpcSendService(
    loggerFactory: Logger.Factory,
    private val protoBuf: ProtoBuf,
    private val session: RpcWebSocketSession
) {

    @Suppress("unused")
    private val log = loggerFactory.getLogger(this::class)

    init {
        RpcSendService.protoBuf = protoBuf
    }

    suspend fun echo(msg: String): Response.Echo {
        val req = Request.Echo(nextId(), msg)
        val res = rpc.request<Response.Echo>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    suspend fun tell(addr: Address, msg: Actor.Message): Response {
        val req = Request.Tell(nextId(), addr, msg)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    suspend fun ask(addr: Address, msg: Actor.Message): Response {
        val req = Request.Ask(nextId(), addr, msg)
        val res = rpc.request<Response>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    suspend fun status(addr: Address): Response.Status {
        val req = Request.Status(nextId(), addr)
        val res = rpc.request<Response.Status>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    suspend fun stats(addr: Address): Response.Stats {
        val req = Request.Stats(nextId(), addr)
        val res = rpc.request<Response.Stats>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
        return res
    }

    suspend fun shutdown(addr: Address) {
        val req = Request.Shutdown(nextId(), addr)
        val res = rpc.request<Response.Empty>(req.id) {
            session.send(req.serialize())
        }
        if (res.id != req.id) error("Sanity check failed :: req.id != res.id.")
    }

    suspend fun close(): Unit = session.close()

    private fun Request.serialize(): ByteArray =
        protoBuf.encodeToByteArray(Request.serializer(), this)

    companion object {
        private lateinit var protoBuf: ProtoBuf
        private var idx: Long = 0
        private val idxMutex = Mutex()
        private suspend fun nextId(): Long = idxMutex.withLock { idx++ }
        private val rpc = RpcManager<Response>(ActorSystem.loggerFactory, 30.seconds)
        suspend fun rpcHandleResponse(bytes: ByteArray) {
            val res = protoBuf.decodeFromByteArray(Response.serializer(), bytes)
            rpc.response(res.id, res)
        }
    }
}