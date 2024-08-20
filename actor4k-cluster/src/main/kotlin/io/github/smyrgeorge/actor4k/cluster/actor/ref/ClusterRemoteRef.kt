package io.github.smyrgeorge.actor4k.cluster.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor.Companion.addressOf
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorSystem
import java.time.Instant

data class ClusterRemoteRef(
    override val shard: String,
    override val name: String,
    override val key: String,
    private val clazz: String,
    val exp: Instant,
    override val address: String = addressOf(name, key)
) : ActorRef(shard, name, key, address) {
    private val cluster = ActorSystem.cluster as ClusterImpl

    override suspend fun tell(msg: Any) {
        val payload: ByteArray = cluster.serde.encode(msg::class.java, msg)
        val message = Envelope.Tell(shard, clazz, key, payload, msg::class.java.name)
        cluster.msg(message).getOrThrow<Unit>()
    }

    override suspend fun <R> ask(msg: Any): R {
        val payload: ByteArray = cluster.serde.encode(msg::class.java, msg)
        val message = Envelope.Ask(shard, clazz, key, payload, msg::class.java.name)
        return cluster.msg(message).getOrThrow()
    }
}
