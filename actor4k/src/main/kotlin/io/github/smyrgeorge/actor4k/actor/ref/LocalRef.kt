package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.Actor.Companion.addressOf
import io.github.smyrgeorge.actor4k.system.ActorSystem

data class LocalRef(
    override val shard: String,
    override val name: String,
    override val key: String,
    val actor: Class<out Actor>,
    override val address: String = addressOf(name, key)
) : ActorRef(shard, name, key, address) {
    override suspend fun tell(msg: Any): Unit =
        ActorSystem.registry.get(this).tell(msg)

    override suspend fun <R> ask(msg: Any): R =
        ActorSystem.registry.get(this).ask(msg)

    suspend fun status(): Actor.Status = ActorSystem.registry.get(this).status()
    suspend fun stop() = ActorSystem.registry.get(this).shutdown()
}
