package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.Shard
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object ActorRegistry {

    // Only stores [Actor.Ref.Local].
    private val registry = ConcurrentHashMap<String, Actor.Ref.Local>(/* initialCapacity = */ 1024)

    suspend fun <A : Actor> get(
        actor: KClass<A>,
        key: Actor.Key,
        shard: Shard.Key = Shard.Key(key.value)
    ): Actor.Ref = get(actor.java, key, shard)

    suspend fun <A : Actor> get(
        actor: Class<A>,
        key: Actor.Key,
        shard: Shard.Key = Shard.Key(key.value)
    ): Actor.Ref {
        val address = Actor.addressOf(actor, key)

        // Check if the actor already exists in the local storage.
        registry[address]?.let { return it }

        // Create Local/Remote actor.
        val ref: Actor.Ref =
            if (ActorSystem.clusterMode
                && ActorSystem.cluster.memberOf(shard).alias() != ActorSystem.cluster.node.alias
            ) {
                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.GetActorRef(shard, actor.name, key)
                ActorSystem.cluster.msg(msg).getOrThrow<Envelope.GetActorRef.Ref>().toRef(shard)
            } else {
                // Case Local.
                // Spawn the actor.
                val ref: Actor.Ref.Local = actor
                    .getConstructor(Shard.Key::class.java, Actor.Key::class.java)
                    .newInstance(shard, key)
                    .ref()

                // Store [Actor.Ref] to the local storage.
                registry[address] = ref
                ref
            }

        return ref
    }

    suspend fun get(clazz: String, key: Actor.Key, shard: Shard.Key): Actor.Ref {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")
        return get(actor, key, shard)
    }

    fun <A : Actor> unregister(actor: Class<A>, key: Actor.Key) {
        val address = Actor.addressOf(actor, key)
        registry[address]?.let {
            if (it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
            registry.remove(address)
        }
    }
}