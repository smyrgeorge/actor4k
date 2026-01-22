package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.ref.Address
import kotlinx.serialization.Serializable

/**
 * A base interface representing messages exchanged in a clustered actor system.
 */
interface RpcEnvelope {
    val id: Long

    @Serializable
    sealed interface Request : RpcEnvelope {
        @Serializable
        data class Echo(override val id: Long, val payload: String) : Request

        @Serializable
        data class Tell(override val id: Long, val addr: Address, val payload: ActorProtocol) : Request

        @Serializable
        data class Ask(
            override val id: Long,
            val addr: Address,
            val payload: ActorProtocol.Message<*>
        ) : Request

        @Serializable
        data class Status(override val id: Long, val addr: Address) : Request

        @Serializable
        data class Stats(override val id: Long, val addr: Address) : Request

        @Serializable
        data class Shutdown(override val id: Long, val addr: Address) : Request

        @Serializable
        data class Terminate(override val id: Long, val addr: Address) : Request
    }

    /**
     * Represents the base type for response messages in a clustered actor system.
     * A response carries information about the outcome or state related to a requested operation.
     */
    @Serializable
    sealed interface Response : RpcEnvelope {
        @Serializable
        class Empty(override val id: Long) : Response

        @Serializable
        class Echo(override val id: Long, val msg: String) : Response

        @Serializable
        class Status(override val id: Long, val status: Actor.Status) : Response

        @Serializable
        class Stats(override val id: Long, val stats: Actor.Stats) : Response

        @Serializable
        class Success(override val id: Long, val response: ActorProtocol.Response) : Response

        @Serializable
        class Failure(override val id: Long, val message: String?, val cause: String?) : Response {
            /**
             * Constructs an exception based on the message and cause properties.
             *
             * @return A newly constructed [IllegalStateException]. If the `cause` property is not null,
             * it wraps the cause into an inner [Exception]; otherwise, the exception includes only the message.
             */
            fun exception(): Exception {
                return if (cause != null) IllegalStateException(message, Exception(cause))
                else IllegalStateException(message)
            }
        }
    }
}