package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.serialization.Serializable

sealed interface Envelope {

    val shard: String

    @Suppress("ArrayInDataClass")
    data class Ask(
        override val shard: String,
        val actorClazz: String,
        val actorKey: String,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Tell(
        override val shard: String,
        val actorClazz: String,
        val actorKey: String,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    data class GetActor(
        override val shard: String,
        val actorClazz: String,
        val actorKey: String
    ) : Envelope {
        @Serializable
        data class Ref(
            val shard: String,
            val clazz: String,
            val name: String,
            val key: String
        ) {
            fun toRef(shard: String): Actor.Ref = Actor.Ref.Remote(shard, name, key, clazz)
        }
    }

    @Suppress("ArrayInDataClass")
    data class Response(
        override val shard: String,
        val payload: ByteArray,
        val payloadClass: String,
        val error: Boolean
    ) : Envelope {
        data class Error(
            val code: Code,
            val message: String
        ) {
            enum class Code {
                SHARD_ACCESS_ERROR,
                UNKNOWN
            }

            fun ex(): Nothing = throw ClusterError(code, message)
            data class ClusterError(val code: Code, override val message: String) : RuntimeException(message)
        }

        fun <T> getOrThrow(): T =
            if (error) Cluster.Response.Error.parseFrom(payload).toError().ex()
            else ActorSystem.cluster.serde.decode(payloadClass, payload)

        companion object {
            fun error(shard: String, error: Error): Response =
                Response(
                    shard = shard,
                    payload = error.toProto().toByteArray(),
                    payloadClass = error::class.java.name,
                    error = true
                )

            fun ok(shard: String, payload: Any): Response =
                Response(
                    shard = shard,
                    payload = ActorSystem.cluster.serde.encode(payload::class.java, payload),
                    payloadClass = payload::class.java.name,
                    error = false
                )
        }
    }
}