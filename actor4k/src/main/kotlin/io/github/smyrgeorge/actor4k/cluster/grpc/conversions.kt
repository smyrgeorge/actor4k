package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.proto.Cluster
import java.util.*

fun Cluster.Pong.toEnvelope() =
    Envelope.Pong(id = UUID.fromString(id), message = message)

fun Cluster.Raw.toEnvelope() =
    Envelope.Raw(payload = payload.toByteArray())

fun Cluster.ActorRef.toEnvelope() =
    Envelope.ActorRef(key = key, name = name)
