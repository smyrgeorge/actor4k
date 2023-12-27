package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.system.ActorSystem
import java.util.*

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
                ShardError
            }
        }

        fun <T> getOrThrow(): T = if (error) {
            val e = ActorSystem.cluster.serde.decode(Error::class.java, payload)
            throw ClusterError(e.code, e.message)
        } else ActorSystem.cluster.serde.decode(payloadClass, payload)

        companion object {
            fun error(shard: Shard.Key, payload: Error) =
                Response(shard, ActorSystem.cluster.serde.encode(payload), payload::class.java.name, true)

            fun of(shard: Shard.Key, payload: Any): Response =
                Response(shard, ActorSystem.cluster.serde.encode(payload), payload::class.java.name, false)
        }
    }
}