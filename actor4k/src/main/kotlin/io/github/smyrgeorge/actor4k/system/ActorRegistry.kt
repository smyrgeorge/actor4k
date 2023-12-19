package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object ActorRegistry {

    // Only stores [Actor.Ref.Local].
    private val registry = ConcurrentHashMap<String, Actor.Ref.Local>(/* initialCapacity = */ 1024)

    suspend fun <A : Actor> get(actor: KClass<A>, key: String): Actor.Ref =
        get(actor.java, key)

    suspend fun <A : Actor> get(actor: Class<A>, key: String): Actor.Ref {
        val name = Actor.nameOf(actor, key)

        // Check if the actor already exists in the local storage.
        registry[name]?.let { return it }

        // Create Local/Remote actor.
        val ref: Actor.Ref =
            if (ActorSystem.clusterMode
                && ActorSystem.cluster.memberOf(name).alias() != ActorSystem.cluster.node.alias
            ) {
                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.Spawn(actor.canonicalName, key)
                ActorSystem.cluster.msg<Envelope.ActorRef>(name, msg).toRef()
            } else {
                // Case Local.
                // Spawn the actor.
                val ref = actor.getConstructor(String::class.java).newInstance(key).ref()
                // Store [Actor.Ref] to the local storage.
                registry[name] = ref
                ref
            }

        return ref
    }

    suspend fun get(clazz: String, key: String): Actor.Ref {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor>
            ?: error("Could not find requested actor class='$clazz'.")
        return get(actor, key)
    }
}