package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.proto.*

fun Cluster.Response.toResponse() =
    Envelope.Response(Shard.Key(shard), payload.toByteArray(), payloadClass, error)

fun Envelope.Ping.toProto(): Cluster.Ping {
    val m = this
    return ping {
        shard = m.shard.value
        id = m.id.toString()
        message = m.message
    }
}

fun Envelope.Ask.toProto(): Cluster.Ask {
    val m = this
    return ask {
        shard = m.shard.value
        actorClazz = m.actorClazz
        actorKey = m.actorKey.value
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Tell.toProto(): Cluster.Tell {
    val m = this
    return tell {
        shard = m.shard.value
        actorClazz = m.actorClazz
        actorKey = m.actorKey.value
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Response.toProto(): Cluster.Response {
    val m = this
    return response {
        shard = m.shard.value
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
        error = m.error
    }
}

fun Envelope.GetActorRef.toProto(): Cluster.GetActor {
    val m = this
    return getActor {
        shard = m.shard.value
        actorClazz = m.actorClazz
        actorKey = m.actorKey.value
    }
}
