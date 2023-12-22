package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import java.util.*

sealed interface Envelope {

    data class Ping(
        val id: UUID,
        val message: String
    ) : Envelope

    data class Pong(
        val id: UUID,
        val message: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Ask(
        val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Tell(
        val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Response(
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    data class GetActorRef(
        val shard: Shard.Key,
        val actorClazz: String,
        val actorKey: Actor.Key
    ) : Envelope

    data class ActorRef(
        val shard: Shard.Key,
        val clazz: String,
        val name: String,
        val key: Actor.Key,
        val node: String
    ) : Envelope {
        fun toRef(shard: Shard.Key): Actor.Ref = Actor.Ref.Remote(shard, name, key, clazz, node)
    }
}