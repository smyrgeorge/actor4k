package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.ClusterActorRegistry
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Request
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage.Response
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.Logger
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalSerializationApi::class)
class RpcReceiveService(
    loggerFactory: Logger.Factory,
    private val protoBuf: ProtoBuf
) {

    private val log = loggerFactory.getLogger(this::class)

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

    fun echo(msg: Request.Echo): Response.Echo = Response.Echo(msg.id, msg.payload)

    suspend fun tell(msg: Request.Tell): Response {
        return try {
            registry.get(msg.addr).tell(msg.payload)
            Response.Empty(msg.id)
        } catch (ex: Throwable) {
            Response.Failure(msg.id, ex.message ?: "", ex.cause?.message)
        }
    }

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

    suspend fun status(msg: Request.Status): Response.Status {
        val res = registry.get(msg.addr).status()
        return Response.Status(msg.id, res)
    }

    suspend fun stats(msg: Request.Stats): Response.Stats {
        val res = registry.get(msg.addr).stats()
        return Response.Stats(msg.id, res)
    }

    suspend fun shutdown(msg: Request.Shutdown): Response.Empty {
        registry.get(msg.addr).shutdown()
        return Response.Empty(msg.id)
    }

    companion object {
        private val registry: ClusterActorRegistry by lazy { ActorSystem.registry as ClusterActorRegistry }

        private object ClusterRpcReceiveServiceScope : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() = EmptyCoroutineContext
        }

        private fun launch(f: suspend () -> Unit) {
            ClusterRpcReceiveServiceScope.launch(Dispatchers.Default) { f() }
        }
    }
}