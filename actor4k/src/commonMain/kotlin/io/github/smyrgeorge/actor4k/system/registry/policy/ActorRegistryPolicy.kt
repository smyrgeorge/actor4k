package io.github.smyrgeorge.actor4k.system.registry.policy

import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry

/**
 * A policy that decides whether an [ActorRegistry] is allowed to admit (create) additional actors.
 *
 * A policy expresses one or more constraints on the registry (for example, the maximum number of
 * actors and, in the future, other factors such as maximum heap memory). It is intentionally kept
 * small and composable: a multi-factor policy can be implemented by combining several single-factor
 * policies, each evaluated against the same registry.
 *
 * Implementations must be thread-safe: [isExceeded] and [describe] may be invoked concurrently from
 * multiple coroutines.
 */
interface ActorRegistryPolicy {
    /**
     * Evaluates whether the policy is currently exceeded for the given [registry].
     *
     * When this returns `true`, the registry is considered to be over its allowed limits and should
     * refuse to create new actors until enough capacity has been freed.
     *
     * @param registry The registry whose current state is evaluated against this policy.
     * @return `true` if the policy is exceeded, `false` otherwise.
     */
    suspend fun isExceeded(registry: ActorRegistry): Boolean

    /**
     * Builds a human-readable explanation of the policy's current state, used to produce a
     * descriptive error message when the policy cannot be satisfied.
     *
     * @param registry The registry whose current state is described.
     * @return A message explaining why (or to what extent) the policy is exceeded.
     */
    suspend fun describe(registry: ActorRegistry): String
}
