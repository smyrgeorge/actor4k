package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.ClusterRpcService.ClusterMessage.Response
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.rpc.RemoteService
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

/**
 * ClusterRpcService is a remote service interface used for communication in a distributed system.
 * It provides functionalities that allow sending and receiving messages, querying status, collecting
 * statistics, and initiating shutdown actions for actors.
 */
@Rpc
interface ClusterRpcService : RemoteService {
    suspend fun echo(msg: String): Response.Echo
    suspend fun tell(addr: Address, msg: Actor.Message): Response
    suspend fun ask(addr: Address, msg: Actor.Message): Response
    suspend fun status(addr: Address): Response.Status
    suspend fun stats(addr: Address): Response.Stats
    suspend fun shutdown(addr: Address): Response.Empty

    /**
     * Represents a contract for messages exchanged in a distributed system.
     * This interface outlines the structure for defining communication
     * between different components or nodes within the system.
     */
    interface ClusterMessage {
        /**
         * Represents a response in a communication process within a distributed system.
         * This sealed interface allows for handling different types of responses that
         * may be received as part of a messaging system.
         */
        sealed interface Response {
            @Serializable
            data object Empty : Response

            @Serializable
            class Echo(val message: String) : Response

            @Serializable
            class Status(val status: Actor.Status) : Response

            @Serializable
            class Stats(val stats: Actor.Stats) : Response

            @Serializable
            class Success(val response: Actor.Message.Response) : Response

            @Serializable
            class Failure(val message: String, val cause: String?) : Response
        }
    }

    class Impl(
        override val coroutineContext: CoroutineContext,
    ) : ClusterRpcService {
        override suspend fun echo(msg: String): Response.Echo = Response.Echo(msg)

        override suspend fun tell(addr: Address, msg: Actor.Message): Response {
            return try {
                registry.get(addr).tell(msg)
                Response.Empty
            } catch (ex: Throwable) {
                Response.Failure(ex.message ?: "", ex.cause?.message)
            }
        }

        override suspend fun ask(addr: Address, msg: Actor.Message): Response {
            val res = registry.get(addr).ask<Actor.Message.Response>(msg)
            return when {
                res.isSuccess -> Response.Success(res.getOrThrow())
                else -> Response.Failure(res.exceptionOrNull()?.message ?: "", res.exceptionOrNull()?.cause?.message)
            }
        }

        override suspend fun status(addr: Address): Response.Status {
            val res = registry.get(addr).status()
            return Response.Status(res)
        }

        override suspend fun stats(addr: Address): Response.Stats {
            val res = registry.get(addr).stats()
            return Response.Stats(res)
        }

        override suspend fun shutdown(addr: Address): Response.Empty {
            registry.get(addr).shutdown()
            return Response.Empty
        }

        companion object {
            private val registry: ClusterActorRegistry by lazy { ActorSystem.registry as ClusterActorRegistry }
        }
    }
}