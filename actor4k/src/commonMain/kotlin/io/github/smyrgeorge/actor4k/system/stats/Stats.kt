package io.github.smyrgeorge.actor4k.system.stats

/**
 * Represents a contract for collecting and managing system statistics,
 * such as the number of actors in the system.
 */
interface Stats {
    /**
     * Represents the number of actors currently managed or registered in the system.
     * This variable is typically used for tracking and collecting statistics
     * about the actor system's state and helps in monitoring its performance or usage.
     */
    var actors: Int

    /**
     * Collects and updates system statistics related to the actor system.
     *
     * This function gathers metrics such as the current number of registered actors and
     * the total number of messages processed by the system. It calculates the number
     * of messages processed during the most recent collection period and updates
     * the relevant statistics accordingly.
     *
     * The primary purpose of this method is to monitor the state and performance
     * of the actor-based system by refreshing key metrics.
     */
    fun collect()
}
