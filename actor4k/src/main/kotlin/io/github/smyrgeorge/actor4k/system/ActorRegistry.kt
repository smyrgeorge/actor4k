package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.cmd.Cmd
import io.github.smyrgeorge.actor4k.actor.cmd.Reply
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

object ActorRegistry {

    // Only stores [Actor.Ref.Local].
    private val registry = ConcurrentHashMap<String, Actor.Ref.Local<*, *>>(/* initialCapacity = */ 1024)

    suspend fun <C : Cmd, R : Reply, A : Actor<C, R>> get(actor: KClass<A>, key: String): Actor.Ref<C, R> =
        get(actor.java, key)

    suspend fun <C : Cmd, R : Reply, A : Actor<C, R>> get(actor: Class<A>, key: String): Actor.Ref<C, R> {
        val name = Actor.nameOf(actor, key)

        // Check if the actor already exists in the local storage.
        registry[name]?.let {
            @Suppress("UNCHECKED_CAST")
            return it as Actor.Ref<C, R>
        }

        // Create Local/Remote actor.
        val ref: Actor.Ref<C, R> =
            if (ActorSystem.clusterMode
                && ActorSystem.cluster.memberOf(name).alias() != ActorSystem.cluster.node.alias
            ) {
                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.Spawn(actor.canonicalName, key)
                ActorSystem.cluster.ask<Envelope.ActorRef>(name, msg).toRef()
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

    suspend fun get(clazz: String, key: String): Actor.Ref<*, *> {
        @Suppress("UNCHECKED_CAST")
        val actor = Class.forName(clazz) as? Class<Actor<Cmd, Reply>>
            ?: error("Could not find requested actor class='$clazz'.")
        return get(actor, key)
    }
}