package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.proto.*
import java.util.*

sealed interface Envelope {

    fun toProto(): Any

    data class Ping(
        val id: UUID,
        val message: String
    ) : Envelope {
        override fun toProto(): Cluster.Ping {
            val m = this
            return ping {
                id = m.id.toString()
                message = m.message
            }
        }
    }

    data class Pong(
        val id: UUID,
        val message: String
    ) : Envelope {
        override fun toProto(): Cluster.Pong {
            val m = this
            return pong {
                id = m.id.toString()
                message = m.message
            }
        }
    }

    @Suppress("ArrayInDataClass")
    data class Raw(
        val payload: ByteArray
    ) : Envelope {
        override fun toProto(): Cluster.Raw {
            val m = this
            return raw {
                payload = ByteString.copyFrom(m.payload)
            }
        }
    }

    data class Spawn(
        val className: String,
        val key: String
    ) : Envelope {
        override fun toProto(): Cluster.Spawn {
            val m = this
            return spawn {
                className = m.className
                key = m.key
            }
        }
    }

    data class ActorRef(
        val name: String,
        val key: String
    ) : Envelope {
        fun <C : Cmd, R : Reply> toRef(): Actor.Ref<C, R> =
            Actor.Ref.Remote(key = key, name = name)

        override fun toProto(): Cluster.ActorRef {
            val m = this
            return actorRef {
                name = m.name
                key = m.key
            }
        }
    }
}