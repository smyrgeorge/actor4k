package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcSendService
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
     * Represents the cluster implementation used for managing distributed actors in the registry.
     * This variable holds a reference to the cluster implementation that facilitates operations
     * such as actor registration, retrieval, and interaction within a distributed system.
     *
     * The `cluster` is initialized lazily and depends on the `register` method call to associate
     * the cluster implementation with the actor registry.
     */
    private lateinit var cluster: ClusterImpl

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
        val service: RpcSendService? = cluster.getServiceFor(address)
        return if (service != null) ClusterActorRef(service.session.node, service, address)
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
    override fun factoryFor(actor: AnyActorClass, factory: ActorFactory): ClusterActorRegistry {
        val name = actor.simpleName ?: error("Anonymous classes are not supported, $actor")
        classes[name] = actor
        super.factoryFor(actor, factory)
        return this
    }

    /**
     * Registers the given cluster implementation with the actor registry.
     *
     * @param cluster The cluster implementation to be registered.
     * @return The updated instance of [ClusterActorRegistry].
     */
    fun register(cluster: ClusterImpl): ClusterActorRegistry {
        this.cluster = cluster
        return this
    }
}