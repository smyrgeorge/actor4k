package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.proto.message
import io.github.smyrgeorge.actor4k.proto.spawn
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

class GrpcClient(host: String, port: Int) : Closeable {

    private val channel: ManagedChannel = NettyChannelBuilder.forAddress(host, port).build()
    private val stub = NodeServiceGrpcKt.NodeServiceCoroutineStub(channel)

    suspend fun message(p: ByteArray): ByteArray {
        val req: Cluster.Message = message { payload = ByteString.copyFrom(p) }
        return stub.message(req).payload.toByteArray()
    }

    suspend fun <C : Cmd, R : Reply> spawn(k: String, n: String): Actor.Ref.Remote<C, R> {
        fun Cluster.ActorRef.toRef() = Actor.Ref.Remote<C, R>(key = key, name = name)

        val req: Cluster.Spawn = spawn {
            className = n
            key = k
        }

        return stub.spawn(req).toRef()
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}