package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.proto.envelope

class NodeService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {
    override suspend fun command(request: Cluster.Envelope): Cluster.Envelope {
        val payload = request.payload.toString()
        println(payload)
        return envelope { this.payload = ByteString.copyFrom("Pong!".toByteArray()) }
    }
}