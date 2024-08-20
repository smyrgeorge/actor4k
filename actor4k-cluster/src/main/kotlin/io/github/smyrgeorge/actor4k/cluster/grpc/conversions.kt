package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.proto.ask
import io.github.smyrgeorge.actor4k.proto.getActor
import io.github.smyrgeorge.actor4k.proto.response
import io.github.smyrgeorge.actor4k.proto.tell
import io.github.smyrgeorge.actor4k.proto.Cluster as ClusterProto

fun ClusterProto.Response.toResponse() =
    Envelope.Response(shard, payload.toByteArray(), payloadClass, error)

fun ClusterProto.Response.Error.toError() =
    ClusterImpl.Error(ClusterImpl.Error.Code.valueOf(code), message)

fun Envelope.Ask.toProto(): ClusterProto.Ask {
    val m = this
    return ask {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.Tell.toProto(): ClusterProto.Tell {
    val m = this
    return tell {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
    }
}

fun Envelope.GetActor.toProto(): ClusterProto.GetActor {
    val m = this
    return getActor {
        shard = m.shard
        actorClazz = m.actorClazz
        actorKey = m.actorKey
    }
}

fun Envelope.Response.toProto(): ClusterProto.Response {
    val m = this
    return response {
        shard = m.shard
        payload = ByteString.copyFrom(m.payload)
        payloadClass = m.payloadClass
        error = m.error
    }
}

fun ClusterImpl.Error.toProto(): ClusterProto.Response.Error {
    val m = this
    return ClusterProto.Response.Error.newBuilder()
        .setCode(m.code.name)
        .setMessage(m.message)
        .build()
}
