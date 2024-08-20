package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.actor.ref.ClusterRemoteRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.serialization.Serializable
import java.time.Instant
import io.github.smyrgeorge.actor4k.proto.Cluster as ClusterProto

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
            val key: String,
        ) {
            private val exp = Instant.now()
                .plusSeconds(ActorSystem.conf.actorRemoteRefExpiration.inWholeSeconds)
                .epochSecond

            fun toRef(shard: String): ClusterRemoteRef = ClusterRemoteRef(
                shard = shard,
                name = name,
                key = key,
                clazz = clazz,
                exp = Instant.ofEpochSecond(exp)
            )
        }
    }

    @Suppress("ArrayInDataClass")
    data class Response(
        override val shard: String,
        val payload: ByteArray,
        val payloadClass: String,
        val error: Boolean
    ) : Envelope {
        fun <T> getOrThrow(): T =
            if (error) ClusterProto.Response.Error.parseFrom(payload).toError().ex()
            else ActorSystem.cluster.serde.decode(payloadClass, payload)

        companion object {
            fun error(shard: String, error: ClusterImpl.Error): Response =
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
