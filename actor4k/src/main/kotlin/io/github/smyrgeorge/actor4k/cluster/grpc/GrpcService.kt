package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
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
            is Envelope.GetActorRef -> getActorRef(m.toProto()).toEnvelope()
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
        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), Shard.Key(request.shard))
        val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
        val res = actor.ask<Any>(msg)
        return Envelope.Response(ActorSystem.cluster.serde.encode(res), res::class.java.canonicalName).toProto()
    }

    override suspend fun tell(request: Cluster.Tell): Cluster.Response {
        ActorSystem.cluster.stats.message()
        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), Shard.Key(request.shard))
        val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
        actor.tell(msg)
        return Envelope.Response(ActorSystem.cluster.serde.encode("."), String::class.java.canonicalName).toProto()
    }

    override suspend fun getActorRef(request: Cluster.GetActorRef): Cluster.ActorRef {
        ActorSystem.cluster.stats.message()
        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), Shard.Key(request.shard))
        return Envelope.ActorRef(
            shard = Shard.Key(request.shard),
            clazz = request.actorClazz,
            name = actor.name,
            key = actor.key,
            node = ActorSystem.cluster.node.alias
        ).toProto()
    }
}