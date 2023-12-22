package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
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
        val actorClazz: String,
        val actorKey: String,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Tell(
        val actorClazz: String,
        val actorKey: String,
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    @Suppress("ArrayInDataClass")
    data class Response(
        val payload: ByteArray,
        val payloadClass: String
    ) : Envelope

    data class GetActorRef(
        val actorClazz: String,
        val actorKey: String
    ) : Envelope

    data class ActorRef(
        val clazz: String,
        val name: String,
        val key: String,
        val node: String
    ) : Envelope {
        fun toRef(): Actor.Ref = Actor.Ref.Remote(name, key, clazz, node)
    }
}