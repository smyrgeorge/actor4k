package io.github.smyrgeorge.actor4k.system.registry.policy

import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry

/**
 * An [ActorRegistryPolicy] that limits the total number of actors a registry may hold.
 *
 * The registry is considered exceeded once it already holds [maxActors] actors, meaning no
 * additional actor can be created until at least one existing actor has been released.
 *
 * @property maxActors The maximum number of actors allowed to be registered at the same time.
 */
class MaxActorsPolicy(
    val maxActors: Int
) : ActorRegistryPolicy {

    init {
        require(maxActors > 0) { "maxActors must be greater than 0, but was $maxActors." }
    }

    override suspend fun isExceeded(registry: ActorRegistry): Boolean = registry.size() >= maxActors

    override suspend fun describe(registry: ActorRegistry): String =
        "Maximum number of actors reached (current=${registry.size()}, limit=$maxActors)."
}
