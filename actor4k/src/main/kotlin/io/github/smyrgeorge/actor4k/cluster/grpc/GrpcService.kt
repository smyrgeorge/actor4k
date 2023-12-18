package io.github.smyrgeorge.actor4k.cluster.grpc

import com.google.protobuf.ByteString
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.proto.actorRef
import io.github.smyrgeorge.actor4k.proto.message
import io.github.smyrgeorge.actor4k.system.ActorRegistry

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {
    override suspend fun message(request: Cluster.Message): Cluster.Message {
        // TODO: fix this.
        val bytes = ByteString.copyFrom("Pong!".toByteArray())
        return message { payload = bytes }
    }

    override suspend fun spawn(request: Cluster.Spawn): Cluster.ActorRef {
        val ref: Actor.Ref.Local<*, *> = ActorRegistry.spawn(request.className, request.key)
        return actorRef {
            this.name = ref.name
            this.key = ref.key
        }
    }
}