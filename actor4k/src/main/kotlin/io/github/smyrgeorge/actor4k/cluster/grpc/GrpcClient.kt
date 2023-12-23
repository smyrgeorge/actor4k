package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class GrpcClient(host: String, port: Int) : Closeable {

    private val channel: ManagedChannel = NettyChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    private val stub = NodeServiceGrpcKt.NodeServiceCoroutineStub(channel)

    suspend fun request(m: Envelope): Envelope.Response =
        when (m) {
            is Envelope.Ping -> stub.ping(m.toProto()).toResponse()
            is Envelope.Ask -> stub.ask(m.toProto()).toResponse()
            is Envelope.Tell -> stub.tell(m.toProto()).toResponse()
            is Envelope.GetActorRef -> stub.getActor(m.toProto()).toResponse()
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }

    override fun close() {
        channel.shutdown().awaitTermination(2, TimeUnit.SECONDS)
    }
}