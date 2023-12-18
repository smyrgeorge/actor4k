package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
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
    data class Raw(
        val payload: ByteArray
    ) : Envelope

    data class Spawn(
        val className: String,
        val key: String
    ) : Envelope

    data class ActorRef(
        val name: String,
        val key: String,
        val node: String
    ) : Envelope {
        fun <C : Cmd, R : Reply> toRef(): Actor.Ref<C, R> =
            Actor.Ref.Remote(key = key, name = name, node = node)
    }
}