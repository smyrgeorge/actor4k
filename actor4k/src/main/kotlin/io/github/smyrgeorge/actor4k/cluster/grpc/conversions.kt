package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.ask
import io.github.smyrgeorge.actor4k.proto.getActor
import io.github.smyrgeorge.actor4k.proto.response
import io.github.smyrgeorge.actor4k.proto.tell

fun Cluster.Response.toResponse() =
    Envelope.Response(shard, payload.toByteArray(), payloadClass, error)

fun Cluster.Response.Error.toError() =
    Envelope.Response.Error(Envelope.Response.Error.Code.valueOf(code), message)

fun Envelope.Ask.toProto(): Cluster.Ask {
    val m = this
    return ask {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Tell.toProto(): Cluster.Tell {
    val m = this
    return tell {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.GetActor.toProto(): Cluster.GetActor {
    val m = this
    return getActor {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
    }
}

fun Envelope.Response.toProto(): Cluster.Response {
    val m = this
    return response {
        shard = m.shard
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
        error = m.error
    }
}

fun Envelope.Response.Error.toProto(): Cluster.Response.Error {
    val m = this
    return Cluster.Response.Error.newBuilder()
        .setCode(m.code.name)
        .setMessage(m.message)
        .build()
}
