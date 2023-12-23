package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorSystem

object ShardManager {

    fun checkShard(shard: Shard.Key): Envelope.Response.Error? {
        if (ActorSystem.cluster.memberOf(shard).alias() != ActorSystem.cluster.node.alias) {
            return Envelope.Response.Error(
                code = Envelope.Response.Error.Code.ShardError,
                message = "Message for requested shard='${shard.value}' is not supported for node='${ActorSystem.cluster.node.alias}'."
            )
        }
        return null
    }

}