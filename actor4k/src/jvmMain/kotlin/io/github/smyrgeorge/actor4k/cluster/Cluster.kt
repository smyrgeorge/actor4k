package io.github.smyrgeorge.actor4k.cluster

/**
 * Interface representing a cluster that can be started and shut down.
 *
 * This interface provides methods to manage the lifecycle of a cluster.
 * It is generally used to define the behavior of a distributed system or service
 * that requires coordinated initialization and cleanup of components.
 */
interface Cluster {
    fun start(): Cluster
    fun shutdown()
}
