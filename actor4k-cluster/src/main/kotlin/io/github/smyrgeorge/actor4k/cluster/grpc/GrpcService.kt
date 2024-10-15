package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.system.ActorSystem
import org.slf4j.LoggerFactory
import io.github.smyrgeorge.actor4k.proto.Cluster as ClusterProto

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(this::class.java)

    suspend fun request(m: Envelope): Envelope.Response =
        when (m) {
            is Envelope.Ask -> ask(m.toProto()).toResponse()
            is Envelope.Tell -> tell(m.toProto()).toResponse()
            is Envelope.GetActor -> getActor(m.toProto()).toResponse()
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }

    override suspend fun ask(request: ClusterProto.Ask): ClusterProto.Response {
        return try {
            val actor = actorRefOf(request.actorClazz, request.actorKey, request.shard)
            val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
            val res = actor.ask<Any>(msg)
            Envelope.Response.ok(request.shard, res).toProto()
        } catch (e: Exception) {
            log.error(e.message, e)
            e.toResponse(request.shard)
        }
    }

    override suspend fun tell(request: ClusterProto.Tell): ClusterProto.Response {
        return try {
            val actor = actorRefOf(request.actorClazz, request.actorKey, request.shard)
            val msg = ActorSystem.cluster.serde.decode<Any>(request.payloadClass, request.payload.toByteArray())
            actor.tell(msg)
            Envelope.Response.ok(request.shard, ".").toProto()
        } catch (e: Exception) {
            log.error(e.message, e)
            e.toResponse(request.shard)
        }
    }

    override suspend fun getActor(request: ClusterProto.GetActor): ClusterProto.Response {
        return try {
            val actor = actorRefOf(request.actorClazz, request.actorKey, request.shard)
            val res = Envelope.GetActor.Ref(request.shard, request.actorClazz, actor.name, actor.key)
            Envelope.Response.ok(request.shard, res).toProto()
        } catch (e: Exception) {
            log.error(e.message, e)
            e.toResponse(request.shard)
        }
    }

    private suspend fun actorRefOf(clazz: String, key: String, shard: String): ActorRef {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")
        return ActorSystem.get(actor, key, shard)
    }

    private fun Exception.toResponse(shard: String): ClusterProto.Response {
        val code = if (this is ClusterImpl.Error.ClusterException) code else ClusterImpl.Error.Code.UNKNOWN
        val error = ClusterImpl.Error(code, message ?: "")
        return Envelope.Response.error(shard, error).toProto()
    }
}
