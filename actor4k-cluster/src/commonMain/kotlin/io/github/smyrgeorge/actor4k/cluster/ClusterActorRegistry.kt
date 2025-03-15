package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcSendService
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.util.extentions.ActorFactory
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass

/**
 * A specialized actor registry implementation for managing and retrieving actor references within a cluster.
 *
 * This class extends the base functionality of [ActorRegistry] by providing cluster-aware features,
 * enabling seamless resolution of actor references both locally and across cluster nodes. It is used to
 * facilitate actor lifecycle management, remote actor communication, and distribution across a cluster.
 */
class ClusterActorRegistry : ActorRegistry() {
    /**
     * A registry mapping actor class names to their respective definitions within the cluster.
     *
     * This map serves as a centralized repository for class-to-actor mappings, facilitating
     * retrieval and registration of actors based on their associated class names. Each entry
     * in the map associates a unique string identifier (typically the actor class name) with
     * its corresponding actor class definition.
     *
     * Used internally by the cluster actor registry to manage actor class instances and
     * ensure that actor references can be resolved correctly during runtime.
     */
    private val classes = mutableMapOf<String, AnyActorClass>()

    /**
     * Retrieves an actor reference for the specified actor class and address.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param address The unique address identifying the actor within the system.
     * @return An [ActorRef] representing either a local or cluster-based reference to the actor.
     */
    override suspend fun get(clazz: AnyActorClass, address: Address): ActorRef {
        // We use the key-hash to achieve efficient sharding between different actor types they share the same key.
        val nodeIdx = address.keyHash % cluster.nodes.size
        val node = cluster.nodes[nodeIdx]
        val service: RpcSendService? = cluster.services[nodeIdx]
        // If a service exists it means that we have to forward the message to another node.
        return if (service != null) ClusterActorRef(node, service, address)
        else super.get(clazz, address)
    }

    /**
     * Retrieves a reference to an actor associated with the given address.
     *
     * @param address The unique address of the actor to be retrieved.
     * @return An [ActorRef] representing the actor associated with the provided address.
     * @throws IllegalStateException If no actor class is found associated with the address.
     */
    internal suspend fun get(address: Address): ActorRef {
        val clazz = classes[address.name] ?: error("No actor class found for $address")
        return get(clazz, address)
    }

    /**
     * Registers the actor class with the provided factory and returns the updated actor registry.
     *
     * @param actor The class of the actor to be registered.
     * @param factory The factory responsible for creating instances of the specified actor class.
     * @return The updated [ActorRegistry] after registering the actor class with the factory.
     * @throws IllegalStateException If the provided actor class has no simple name (i.e., is an anonymous class).
     */
    override fun factoryFor(actor: AnyActorClass, factory: ActorFactory): ActorRegistry {
        val name = actor.simpleName ?: error("Anonymous classes are not supported, $actor")
        classes[name] = actor
        return super.factoryFor(actor, factory)
    }

    companion object {
        private val cluster: ClusterImpl by lazy { ActorSystem.cluster as ClusterImpl }
    }
}