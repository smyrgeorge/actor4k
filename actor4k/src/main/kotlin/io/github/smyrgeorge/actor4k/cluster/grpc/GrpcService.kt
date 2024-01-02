package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.shard.Shard
import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    private val log = KotlinLogging.logger {}

    suspend fun request(m: Envelope): Envelope.Response =
        when (m) {
            is Envelope.Ask -> ask(m.toProto()).toResponse()
            is Envelope.Tell -> tell(m.toProto()).toResponse()
            is Envelope.GetActor -> getActor(m.toProto()).toResponse()
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }


    override suspend fun ask(request: Cluster.Ask): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.canHandle(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        return try {
            val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
            val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
            val res = actor.ask<Any>(msg)
            Envelope.Response.ok(shard, res).toProto()
        } catch (e: Exception) {
            log.error(e) { e.message }
            e.toResponse(shard)
        }
    }

    override suspend fun tell(request: Cluster.Tell): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.canHandle(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        return try {
            val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
            val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
            actor.tell(msg)
            Envelope.Response.ok(shard, ".").toProto()
        } catch (e: Exception) {
            log.error(e) { e.message }
            e.toResponse(shard)
        }
    }

    override suspend fun getActor(request: Cluster.GetActor): Cluster.Response {
        ActorSystem.cluster.stats.message()

        val shard = Shard.Key(request.shard)
        ShardManager.canHandle(shard)?.let {
            return Envelope.Response.error(shard, it).toProto()
        }

        return try {
            val actor = ActorRegistry.get(request.actorClazz, Actor.Key(request.actorKey), shard)
            val res = Envelope.GetActor.Ref(shard, request.actorClazz, actor.name, actor.key)
            Envelope.Response.ok(shard, res).toProto()
        } catch (e: Exception) {
            log.error(e) { e.message }
            e.toResponse(shard)
        }
    }

    private fun Exception.toResponse(shard: Shard.Key): Cluster.Response {
        val code = Envelope.Response.Error.Code.Unknown
        val error = Envelope.Response.Error(code, message ?: "")
        return Envelope.Response.error(shard, error).toProto()
    }
}