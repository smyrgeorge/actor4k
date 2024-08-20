package io.github.smyrgeorge.actor4k.cluster.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.Actor.Companion.addressOf
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.system.ActorSystem

data class ClusterLocalRef(
    override val shard: String,
    override val name: String,
    override val key: String,
    val actor: Class<out Actor>,
    override val address: String = addressOf(name, key)
) : ActorRef(shard, name, key, address) {
    private val cluster = ActorSystem.cluster as ClusterImpl

    private fun ClusterLocalRef.toLocalRef(): LocalRef =
        LocalRef(shard, name, key, actor, address)

    override suspend fun tell(msg: Any) {
        // Check if the requested shard is locked.
        if (ActorSystem.isCluster()) cluster.shardIsLocked(shard)?.ex()
        ActorSystem.registry.get(this.toLocalRef()).tell(msg)
    }

    override suspend fun <R> ask(msg: Any): R {
        // Check if the requested shard is locked.
        if (ActorSystem.isCluster()) cluster.shardIsLocked(shard)?.ex()
        return ActorSystem.registry.get(this.toLocalRef()).ask(msg)
    }

    suspend fun status(): Actor.Status = ActorSystem.registry.get(this.toLocalRef()).status()
    suspend fun stop() = ActorSystem.registry.get(this.toLocalRef()).shutdown()
}
