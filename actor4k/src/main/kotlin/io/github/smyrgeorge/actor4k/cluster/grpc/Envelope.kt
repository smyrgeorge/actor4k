package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.shard.Shard
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.serialization.Serializable

sealed interface Envelope {

    val shard: Shard.Key

    @Suppress("ArrayInDataClass")
    data class Ask(
        override val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Tell(
        override val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    data class GetActor(
        override val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key
    ) : Envelope {
        @Serializable
        data class Ref(
            val shard: Shard.Key,
            val clazz: String,
            val name: String,
            val key: Actor.Key
        ) {
            fun toRef(shard: Shard.Key): Actor.Ref = Actor.Ref.Remote(shard, name, key, clazz)
        }
    }

    @Suppress("ArrayInDataClass")
    data class Response(
        override val shard: Shard.Key,
        val payload: ByteArray,
        val payloadClass: String,
        val error: Boolean
    ) : Envelope {
        data class Error(
            val code: Code,
            val message: String
        ) {
            enum class Code {
                ShardError,
                Unknown
            }

            fun ex(): Nothing = throw ClusterError(code, message)
            data class ClusterError(val code: Code, override val message: String) : RuntimeException(message)
        }

        fun <T> getOrThrow(): T =
            if (error) Cluster.Response.Error.parseFrom(payload).toError().ex()
            else ActorSystem.cluster.serde.decode(payloadClass, payload)

        companion object {
            fun error(shard: Shard.Key, error: Error): Response =
                Response(
                    shard = shard,
                    payload = error.toProto().toByteArray(),
                    payloadClass = error::class.java.name,
                    error = true
                )

            fun ok(shard: Shard.Key, payload: Any): Response =
                Response(
                    shard = shard,
                    payload = ActorSystem.cluster.serde.encode(payload::class.java, payload),
                    payloadClass = payload::class.java.name,
                    error = false
                )
        }
    }
}