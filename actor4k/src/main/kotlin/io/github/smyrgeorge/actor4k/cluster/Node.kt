package io.github.smyrgeorge.actor4k.cluster

import io.scalecube.net.Address

data class Node(
    val alias: String,
    val host: String,
    val namespace: String,
    val grpcPort: Int,
    val gossipPort: Int,
    val seedMembers: List<Address>
) {

    class Builder {
        private lateinit var alias: String
        private lateinit var host: String
        private lateinit var namespace: String
        private var grpcPort: Int = 61100
        private var gossipPort: Int = 61000
        private var seedMembers: List<Address> = emptyList()

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

        fun gossipPort(v: Int): Builder {
            gossipPort = v
            return this
        }

        fun seedMembers(v: List<Address>): Builder {
            seedMembers = v
            return this
        }

        fun build(): Node = Node(
            alias = alias,
            host = host,
            namespace = namespace,
            grpcPort = grpcPort,
            gossipPort = gossipPort,
            seedMembers = seedMembers
        )
    }
}