package io.github.smyrgeorge.actor4k.cluster.system.registry

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.actor.ref.ClusterLocalRef
import io.github.smyrgeorge.actor4k.cluster.actor.ref.ClusterRemoteRef
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.util.callSuspend
import io.github.smyrgeorge.actor4k.util.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.reflect.InvocationTargetException
import java.time.Instant

class ClusterActorRegistry : ActorRegistry() {
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }

    init {
        launch {
            while (true) {
                delay(ActorSystem.conf.registryCleanup)
                removeRemoteExpired()
            }
        }
    }

    // Stores [Actor.Ref.Remote].
    private val remote: MutableMap<String, ClusterRemoteRef> = mutableMapOf()
    private val remoteMutex = Mutex()

    override suspend fun <A : Actor> get(actor: Class<A>, key: String, shard: String): ActorRef {
        // Calculate the actor address.
        val address: String = Actor.addressOf(actor, key)

        if (ActorSystem.status != ActorSystem.Status.READY)
            error("Cannot get/create $address, cluster is ${ActorSystem.status}.")

        val (ref: ActorRef, actorInstance: Actor?) =
            // Create Local/Remote actor.
            if (cluster.nodeOf(shard).dc != cluster.conf.alias) {
                // Limit the concurrent access to one at a time.
                // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
                remoteMutex.withLock {
                    val existing = remote[address]
                    if (existing != null && Instant.now().isBefore(existing.exp)) return existing
                    else remote.remove(address)
                }

                // Case Remote.
                // Forward the [Envelope.Spawn] message to the correct cluster node.
                val msg = Envelope.GetActor(shard, actor.name, key)
                val ref = cluster
                    .msg(msg)
                    .getOrThrow<Envelope.GetActor.Ref>()
                    .toRef(shard)
                    .also {
                        remoteMutex.withLock {
                            remote[address] = it
                        }
                    }
                ref to null
            } else {
                // Limit the concurrent access to one at a time.
                // This is critical, because we need to ensure that only one Actor (with the same key) will be created.
                mutex.withLock {
                    // Check if the actor already exists in the local storage.
                    local[address]?.let { return it.ref() }

                    // Case Local.
                    // Spawn the actor.
                    val a: Actor = actor
                        .getConstructor(String::class.java, String::class.java)
                        .newInstance(shard, key)

                    // Store [Actor.Ref] to the local storage.
                    local[address] = a

                    // Declare shard to the [ShardManager]
                    cluster.registerShard(shard)

                    a.ref().toClusterLocalRef() to a
                }
            }

        // Invoke activate (initialization) method.
        @Suppress("DuplicatedCode")
        actorInstance?.let {
            try {
                actor.callSuspend(it, "activate")
            } catch (e: InvocationTargetException) {
                log.error("Could not activate ${it.address()}. Reason: ${e.targetException.message}")
                unregister(actor = actor, key = key, force = true)
                throw e
            } catch (e: Exception) {
                log.error("Could not activate ${it.address()}.")
                unregister(actor = actor, key = key, force = true)
                throw e
            }
        }

        log.debug("Actor $address created and activated successfully.")
        return ref
    }

    override suspend fun <A : Actor> unregister(actor: Class<A>, key: String, force: Boolean) {
        val address = Actor.addressOf(actor, key)
        mutex.withLock {
            local[address]?.let {
                if (!force && it.status() != Actor.Status.FINISHED) error("Cannot unregister $address while is ${it.status()}.")
                local.remove(address)
                cluster.unregisterShard(it.shard)
                log.info("Unregistered actor $address.")
            }
        }
    }

    private suspend fun removeRemoteExpired(): Unit = remoteMutex.withLock {
        log.debug("Removing all remote expired actors.")
        remote.values.forEach {
            if (Instant.now().isAfter(it.exp)) remote.remove(it.address)
        }
    }

    private fun LocalRef.toClusterLocalRef(): ClusterLocalRef =
        ClusterLocalRef(shard, name, key, actor, address)
}
