package io.github.smyrgeorge.actor4k.system.registry

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.system.registry.policy.ActorRegistryPolicy
import io.github.smyrgeorge.actor4k.util.Logger
import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An [ActorRegistry] that constrains the actors it may hold according to an [ActorRegistryPolicy].
 *
 * Before creating a *new* actor the registry consults its [policy]. If the policy is exceeded, the
 * registry first tries to reclaim capacity by aggressively stopping inactive (expired) actors and
 * then re-checks the policy a few times. If capacity cannot be reclaimed within that window, the
 * creation fails with a descriptive [IllegalStateException].
 *
 * Only the creation of new actors is subject to the policy: retrieving an actor that is already
 * registered always succeeds, even when the registry is at capacity.
 *
 * The check is best-effort. Because the policy is evaluated before the (separately locked) actor
 * creation, and because stopping actors is asynchronous, a small, transient overshoot of the limit
 * is possible under highly concurrent creation of distinct actors. The registry never grows without
 * bound: once at capacity, further new actors are rejected.
 *
 * @constructor Creates a bounded registry backed by the given [policy].
 * @param loggerFactory The factory used to obtain a logger for the registry.
 * @param policy The policy that governs whether new actors may be created.
 */
class BoundedActorRegistry(
    loggerFactory: Logger.Factory,
    private val policy: ActorRegistryPolicy,
) : ActorRegistry(loggerFactory) {

    /**
     * Retrieves an [ActorRef] for the given actor class and address, enforcing the [policy] whenever
     * a new actor would have to be created.
     *
     * @param clazz The class of the actor to be retrieved.
     * @param address The address of the actor.
     * @return An [ActorRef] corresponding to the requested actor.
     * @throws IllegalStateException If the policy is exceeded and enough capacity cannot be freed.
     */
    override suspend fun get(clazz: AnyActorClass, address: Address): ActorRef {
        // The policy only constrains the creation of *new* actors. Retrieving an actor that is
        // already registered must always succeed, even when the registry is at capacity.
        if (!contains(address)) enforcePolicy()
        return super.get(clazz, address)
    }

    /**
     * Ensures the [policy] is satisfied before a new actor is created.
     *
     * If the policy is already satisfied this returns immediately. Otherwise it triggers a cleanup of
     * expired actors and re-checks the policy up to [POLICY_RETRY_ATTEMPTS] times, waiting
     * [POLICY_RETRY_DELAY] between attempts to give the asynchronous shutdowns time to complete. If
     * the policy is still exceeded afterwards it throws an [IllegalStateException] explaining why.
     */
    private suspend fun enforcePolicy() {
        // Fast path: nothing to do while the policy is satisfied.
        if (!policy.isExceeded(this)) return

        log.warn(
            "Actor registry policy exceeded, trying to release inactive actors aggressively. Reason: {}",
            policy.describe(this)
        )

        // First, try to reclaim capacity by stopping expired/inactive actors.
        stopLocalExpired()

        // Stopping actors is asynchronous (they unregister once their mailbox drains), so re-check a
        // few times, giving the shutdowns a short window to complete.
        repeat(POLICY_RETRY_ATTEMPTS) {
            if (!policy.isExceeded(this)) return
            delay(POLICY_RETRY_DELAY)
        }

        // Still exceeded after exhausting all retries: give up and fail with a descriptive error.
        if (policy.isExceeded(this)) {
            val reason = policy.describe(this)
            val message = "Cannot create a new actor: the actor registry policy is exceeded. " +
                    "Tried to release inactive actors (stopLocalExpired) and re-checked " +
                    "$POLICY_RETRY_ATTEMPTS times every ${POLICY_RETRY_DELAY.inWholeMilliseconds}ms, " +
                    "but the policy is still exceeded. $reason"
            log.error(message)
            error(message)
        }
    }

    companion object {
        /** How many times the policy is re-checked after triggering a cleanup. */
        private const val POLICY_RETRY_ATTEMPTS: Int = 5

        /** How long to wait between policy re-checks. */
        private val POLICY_RETRY_DELAY: Duration = 100.milliseconds
    }
}
