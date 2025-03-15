package io.github.smyrgeorge.actor4k.cluster

/**
 * Represents a node in a cluster with an alias and its associated address.
 *
 * This class is commonly leveraged to identify and interact with specific nodes in a cluster,
 * particularly in distributed systems. Each node is uniquely identified using a combination of its alias
 * and address.
 *
 * @property alias The human-readable label for the node, used for identification within the cluster.
 * @property address The network address of the node, typically in the form of a URL or socket address.
 */
data class ClusterNode(
    val alias: String,
    val address: String
) {
    @Suppress("unused")
    val host: String by lazy {
        val host = address.substringBeforeLast(":", "")
        if (host.isEmpty()) error("Invalid address: $address")
        host
    }

    val port: Int by lazy {
        val port = address.substringAfterLast(":", "")
        if (port.isEmpty()) error("Invalid address: $address")
        port.toInt()
    }

    /**
     * Returns the string representation of the `ClusterNode`.
     *
     * The representation is formatted as "alias::address", where `alias` is the node's human-readable label
     * and `address` is its network location.
     *
     * @return A string representation of the `ClusterNode` in the format "alias::address".
     */
    override fun toString(): String = "$alias::$address"

    companion object {
        /**
         * Creates a `ClusterNode` instance by parsing a string representation into alias and address parts.
         *
         * The input string must follow the format "alias::address". If the string does not match this format,
         * an error is thrown. The alias represents the human-readable label for the node, and the address represents
         * its network location.
         *
         * @param value The string representation of the cluster node, formatted as "alias::address".
         * @return A `ClusterNode` instance created from the parsed alias and address.
         */
        fun of(value: String): ClusterNode {
            val split = value.split("::")
            if (split.size != 2) error("Invalid node address: $value")
            return ClusterNode(split[0], split[1])
        }
    }
}