package io.github.smyrgeorge.actor4k.cluster

/**
 * Interface representing a cluster that can be started and shut down.
 *
 * This interface provides methods to manage the lifecycle of a cluster.
 * It is generally used to define the behavior of a distributed system or service
 * that requires coordinated initialization and cleanup of components.
 */
interface Cluster {
    /**
     * Starts the cluster, initiating its lifecycle and transitioning it to the operational state.
     *
     * This method is responsible for setting up and preparing the cluster for use. It ensures
     * that all necessary preconditions are met and that the cluster is properly initialized.
     *
     * @param wait If `true`, the method blocks until the server is fully started.
     *             If `false`, it returns immediately after invoking the server's start mechanism.
     */
    fun start(wait: Boolean)

    /**
     * Initiates the shutdown process for the cluster, transitioning it to a non-operational state.
     *
     * This method handles the orderly termination of cluster activities, including:
     * - Informing the cluster network of the shutdown process.
     * - Terminating all registered actors and waiting for them to finish.
     * - Handling cluster-specific cleanup tasks if the current system is a cluster.
     * - Ensuring proper exit behavior depending on the shutdown trigger.
     *
     * It is designed to be used in scenarios where safe and coordinated termination of cluster
     * resources is required.
     */
    fun shutdown()
}
