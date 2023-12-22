package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.proto.*
import java.util.*

fun Cluster.Pong.toEnvelope() =
    Envelope.Pong(UUID.fromString(id), message)

fun Cluster.Response.toEnvelope() =
    Envelope.Response(payload.toByteArray(), payloadClass)

fun Cluster.ActorRef.toEnvelope() =
    Envelope.ActorRef(clazz, name, key, node)


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

fun Envelope.Ask.toProto(): Cluster.Ask {
    val m = this
    return ask {
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Tell.toProto(): Cluster.Tell {
    val m = this
    return tell {
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Response.toProto(): Cluster.Response {
    val m = this
    return response {
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.GetActorRef.toProto(): Cluster.GetActorRef {
    val m = this
    return getActorRef {
        actorClazz = m.actorClazz
        actorKey = m.actorKey
    }
}

fun Envelope.ActorRef.toProto(): Cluster.ActorRef {
    val m = this
    return actorRef {
        clazz = m.clazz
        name = m.name
        key = m.key
        node = m.node
    }
}