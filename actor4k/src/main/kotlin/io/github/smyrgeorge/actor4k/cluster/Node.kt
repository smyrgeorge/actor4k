package io.github.smyrgeorge.actor4k.cluster

import io.scalecube.net.Address

data class Node(
    val alias: String,
    val host: String,
    val namespace: String,
    val grpcPort: Int,
    val initialGroupMembers: List<Pair<String, Address>>
) {

    class Builder {
        private lateinit var alias: String
        private lateinit var host: String
        private lateinit var namespace: String
        private var grpcPort: Int = 50051
        private var initialGroupMembers: List<Pair<String, Address>> = emptyList()

        fun alias(v: String): Builder {
            alias = v
            return this
        }

        fun host(v: String): Builder {
            host = v
            return this
        }

        fun namespace(v: String): Builder {
            namespace = v
            return this
        }

        fun grpcPort(v: Int): Builder {
            grpcPort = v
            return this
        }

        fun initialGroupMembers(v: List<Pair<String, Address>>): Builder {
            initialGroupMembers = v
            return this
        }

        fun build(): Node = Node(
            alias = alias,
            host = host,
            namespace = namespace,
            grpcPort = grpcPort,
            initialGroupMembers = initialGroupMembers
        )
    }
}