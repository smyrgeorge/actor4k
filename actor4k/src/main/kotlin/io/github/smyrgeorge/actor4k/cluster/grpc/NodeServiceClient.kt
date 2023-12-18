package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.proto.envelope
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class NodeServiceClient(port: Int) : Closeable {
    private val channel = ManagedChannelBuilder
        .forAddress("localhost", port)
        .usePlaintext()
        .build()
    private val stub = NodeServiceGrpcKt.NodeServiceCoroutineStub(channel)

    suspend fun command(payload: ByteArray) {
        val req: Cluster.Envelope = envelope { this.payload = ByteString.copyFrom(payload) }
        val res: Cluster.Envelope = stub.command(req)
        println("Received: $res")
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}