package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.proto.actorRef
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import java.util.*

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    suspend fun request(m: Envelope): Envelope =
        when (m) {
            is Envelope.Ping -> ping(m.toProto()).toEnvelope()
            is Envelope.Raw -> raw(m.toProto()).toEnvelope()
            is Envelope.Spawn -> spawn(m.toProto()).toEnvelope()
            is Envelope.Pong -> error("Not a valid gRPC method found.")
            is Envelope.ActorRef -> error("Not a valid gRPC method found.")
        }

    override suspend fun ping(request: Cluster.Ping): Cluster.Pong {
        ActorSystem.cluster.stats.message()
        return Envelope.Pong(id = UUID.fromString(request.id), message = "Pong!").toProto()
    }

    override suspend fun raw(request: Cluster.Raw): Cluster.Raw {
        ActorSystem.cluster.stats.message()
        TODO("Not implemented yer!")
    }

    override suspend fun spawn(request: Cluster.Spawn): Cluster.ActorRef {
        ActorSystem.cluster.stats.message()
        val ref: Actor.Ref<*, *> = ActorRegistry.get(request.className, request.key)
        return Envelope.ActorRef(name = ref.name, key = ref.key).toProto()
    }
}