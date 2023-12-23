package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.ShardManager
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import java.util.*

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    suspend fun request(m: Envelope): Envelope.Response =
        when (m) {
            is Envelope.Ping -> ping(m.toProto()).toResponse()
            is Envelope.Ask -> ask(m.toProto()).toResponse()
            is Envelope.Tell -> tell(m.toProto()).toResponse()
            is Envelope.GetActorRef -> getActor(m.toProto()).toResponse()
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }

    override suspend fun ping(request: Cluster.Ping): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.checkShard(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        val pong = Envelope.Ping.Pong(UUID.fromString(request.id), "Pong!")
        return Envelope.Response.of(shard, pong).toProto()
    }

    override suspend fun ask(request: Cluster.Ask): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.checkShard(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
        val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
        val res = actor.ask<Any>(msg)
        return Envelope.Response.of(shard, res).toProto()
    }

    override suspend fun tell(request: Cluster.Tell): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.checkShard(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
        val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
        actor.tell(msg)
        return Envelope.Response.of(shard, ".").toProto()
    }

    override suspend fun getActor(request: Cluster.GetActor): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.checkShard(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
        val res = Envelope.GetActorRef.Ref(shard, request.actorClazz, actor.name, actor.key)
        return Envelope.Response.of(shard, res).toProto()
    }
}