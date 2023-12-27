package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftMessage
import io.github.smyrgeorge.actor4k.proto.*
import io.github.smyrgeorge.actor4k.util.toByteArray

fun Cluster.Response.toResponse() =
    Envelope.Response(Shard.Key(shard), payload.toByteArray(), payloadClass, error)

fun ClusterRaftMessage.RaftProtocol.toProto(): Cluster.RaftProtocol {
    val m = this
    return raftProtocol {
        payload = ByteString.copyFrom(m.message.toByteArray())
    }
}

fun ClusterRaftMessage.RaftFollowerReady.toProto(): Cluster.RaftFollowerReady {
    val m = this
    return raftFollowerReady {
        alias = m.alias
        host = m.host
        port = m.port
    }
}

fun ClusterRaftMessage.RaftFollowerIsLeaving.toProto(): Cluster.RaftFollowerIsLeaving {
    val m = this
    return raftFollowerIsLeaving {
        alias = m.alias
    }
}

fun ClusterRaftMessage.RaftNewLearner.toProto(): Cluster.RaftNewLearner {
    val m = this
    return raftNewLearner {
        alias = m.alias
        host = m.host
        port = m.port
    }
}

fun ClusterRaftMessage.RaftPing.toProto(): Cluster.RaftPing {
    val m = this
    return raftPing {
        id = m.id.toString()
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

fun Envelope.GetActor.toProto(): Cluster.GetActor {
    val m = this
    return getActor {
        shard = m.shard.value
        actorClazz = m.actorClazz
        actorKey = m.actorKey.value
    }
}
