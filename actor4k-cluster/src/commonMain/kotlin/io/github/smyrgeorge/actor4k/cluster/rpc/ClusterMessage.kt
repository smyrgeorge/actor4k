package io.github.smyrgeorge.actor4k.cluster.rpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.Address
import kotlinx.serialization.Serializable

interface ClusterMessage {
    val id: Long

    @Serializable
    sealed interface Request : ClusterMessage {
        @Serializable
        data class Echo(override val id: Long, val payload: String) : Request

        @Serializable
        data class Tell(override val id: Long, val addr: Address, val payload: Actor.Message) : Request

        @Serializable
        data class Ask(override val id: Long, val addr: Address, val payload: Actor.Message) : Request

        @Serializable
        data class Status(override val id: Long, val addr: Address) : Request

        @Serializable
        data class Stats(override val id: Long, val addr: Address) : Request

        @Serializable
        data class Shutdown(override val id: Long, val addr: Address) : Request
    }

    @Serializable
    sealed interface Response : ClusterMessage {
        @Serializable
        class Empty(override val id: Long) : Response

        @Serializable
        class Echo(override val id: Long, val msg: String) : Response

        @Serializable
        class Status(override val id: Long, val status: Actor.Status) : Response

        @Serializable
        class Stats(override val id: Long, val stats: Actor.Stats) : Response

        @Serializable
        class Success(override val id: Long, val response: Actor.Message.Response) : Response

        @Serializable
        class Failure(override val id: Long, val message: String, val cause: String?) : Response
    }
}