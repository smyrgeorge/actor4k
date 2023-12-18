package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.proto.*
import java.util.*

fun Cluster.Pong.toEnvelope() =
    Envelope.Pong(id = UUID.fromString(id), message = message)

fun Cluster.Raw.toEnvelope() =
    Envelope.Raw(payload = payload.toByteArray())

fun Cluster.ActorRef.toEnvelope() =
    Envelope.ActorRef(key = key, name = name, node = node)


fun Envelope.Ping.toProto(): Cluster.Ping {
    val m = this
    return ping {
        id = m.id.toString()
        message = m.message
    }
}

fun Envelope.Pong.toProto(): Cluster.Pong {
    val m = this
    return pong {
        id = m.id.toString()
        message = m.message
    }
}

fun Envelope.Raw.toProto(): Cluster.Raw {
    val m = this
    return raw {
        payload = ByteString.copyFrom(m.payload)
    }
}

fun Envelope.Spawn.toProto(): Cluster.Spawn {
    val m = this
    return spawn {
        className = m.className
        key = m.key
    }
}

fun Envelope.ActorRef.toProto(): Cluster.ActorRef {
    val m = this
    return actorRef {
        name = m.name
        key = m.key
        node = m.node
    }
}
