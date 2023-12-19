package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import java.util.*

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    suspend fun request(m: Envelope): Envelope =
        when (m) {
            is Envelope.Ping -> ping(m.toProto()).toEnvelope()
            is Envelope.Ask -> ask(m.toProto()).toEnvelope()
            is Envelope.Tell -> tell(m.toProto()).toEnvelope()
            is Envelope.Spawn -> spawn(m.toProto()).toEnvelope()
            is Envelope.Pong -> error("Not a valid gRPC method found.")
            is Envelope.ActorRef -> error("Not a valid gRPC method found.")
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }

    override suspend fun ping(request: Cluster.Ping): Cluster.Pong {
        ActorSystem.cluster.stats.message()
        return Envelope.Pong(UUID.fromString(request.id), "Pong!").toProto()
    }

    override suspend fun ask(request: Cluster.Ask): Cluster.Response {
        ActorSystem.cluster.stats.message()
        val actor = ActorRegistry.get(request.clazz, request.key)
        val clazz: Class<*> = ActorSystem.cluster.serde.loadClass(request.payloadClass)
        val cmd = ActorSystem.cluster.serde.decode<Any>(clazz, request.payload.toByteArray())
//        val res = actor.ask<Any>(cmd)
        TODO()
    }

    override suspend fun tell(request: Cluster.Tell): Cluster.Response {
        ActorSystem.cluster.stats.message()
        val actor = ActorRegistry.get(request.clazz, request.key)
        val clazz: Class<*> = ActorSystem.cluster.serde.loadClass(request.payloadClass)
        val cmd = ActorSystem.cluster.serde.decode<Any>(clazz, request.payload.toByteArray())
        actor.tell(cmd)
        return Envelope.Response(ActorSystem.cluster.serde.encode("."), String::class.java.canonicalName).toProto()
    }

    override suspend fun spawn(request: Cluster.Spawn): Cluster.ActorRef {
        ActorSystem.cluster.stats.message()
        val ref = ActorRegistry.get(request.clazz, request.key)
        return Envelope.ActorRef(request.clazz, ref.name, ref.key, ActorSystem.cluster.node.alias).toProto()
    }
}