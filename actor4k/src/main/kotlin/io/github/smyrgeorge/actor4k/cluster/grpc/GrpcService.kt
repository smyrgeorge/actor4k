package io.github.smyrgeorge.actor4k.cluster.grpc

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.ShardManager
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftEndpoint
import io.github.smyrgeorge.actor4k.cluster.raft.ClusterRaftStateMachine
import io.github.smyrgeorge.actor4k.proto.Cluster
import io.github.smyrgeorge.actor4k.proto.NodeServiceGrpcKt
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.toInstance
import io.microraft.MembershipChangeMode
import io.microraft.RaftRole

class GrpcService : NodeServiceGrpcKt.NodeServiceCoroutineImplBase() {

    private val log = KotlinLogging.logger {}

    suspend fun request(m: Envelope): Envelope.Response =
        when (m) {
            is Envelope.Ask -> ask(m.toProto()).toResponse()
            is Envelope.Tell -> tell(m.toProto()).toResponse()
            is Envelope.GetActor -> getActor(m.toProto()).toResponse()
            is Envelope.Response -> error("Not a valid gRPC method found.")
        }

    override suspend fun raftPing(request: Cluster.RaftPing): Cluster.Response {
        ActorSystem.cluster.stats.protocol()
        return Envelope.Response.of(Shard.Key("."), ".").toProto()
    }

    override suspend fun raftProtocol(request: Cluster.RaftProtocol): Cluster.Response {
        ActorSystem.cluster.stats.protocol()
        ActorSystem.cluster.raft.handle(request.payload.toByteArray().toInstance())
        return Envelope.Response.of(Shard.Key("."), ".").toProto()
    }

    override suspend fun raftFollowerReady(request: Cluster.RaftFollowerReady): Cluster.Response {
        ActorSystem.cluster.stats.protocol()
        val req = ClusterRaftStateMachine.NodeAdded(request.alias, request.host, request.port)
        ActorSystem.cluster.raft.replicate<Unit>(req).join()
        return Envelope.Response.of(Shard.Key("."), ".").toProto()
    }

    override suspend fun raftFollowerIsLeaving(request: Cluster.RaftFollowerIsLeaving): Cluster.Response {
        ActorSystem.cluster.stats.protocol()
        if (ActorSystem.cluster.raft.report.join().result.role == RaftRole.LEADER) {
            val req = ClusterRaftStateMachine.NodeRemoved(request.alias)
            val follower = ActorSystem.cluster.raft.committedMembers.members.firstOrNull { it.id == req.alias }
            follower?.let {
                it as ClusterRaftEndpoint
                ActorSystem.cluster.raft.changeMembership(
                    /* endpoint = */ ClusterRaftEndpoint(req.alias, it.host, it.port),
                    /* mode = */ MembershipChangeMode.REMOVE_MEMBER,
                    /* expectedGroupMembersCommitIndex = */ ActorSystem.cluster.raft.committedMembers.logIndex
                ).join().result
            }

            ActorSystem.cluster.raft.replicate<Unit>(req).join()
        }
        return Envelope.Response.of(Shard.Key("."), ".").toProto()
    }

    override suspend fun raftNewLearner(request: Cluster.RaftNewLearner): Cluster.Response {
        try {
            if (ActorSystem.cluster.raft.report.join().result.role == RaftRole.LEADER) {
                val req = ClusterRaftStateMachine.NodeAdded(request.alias, request.host, request.port)
                ActorSystem.cluster.raft.changeMembership(
                    /* endpoint = */ ClusterRaftEndpoint(req.alias, req.host, req.port),
                    /* mode = */ MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER,
                    /* expectedGroupMembersCommitIndex = */ ActorSystem.cluster.raft.committedMembers.logIndex
                ).join().result

                ActorSystem.cluster.raft.replicate<Unit>(req).join()
            }
        } catch (e: Exception) {
            log.error(e) { e.message }
        }

        return Envelope.Response.of(Shard.Key("."), ".").toProto()
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
        val res = Envelope.GetActor.Ref(shard, request.actorClazz, actor.name, actor.key)
        return Envelope.Response.of(shard, res).toProto()
    }
}