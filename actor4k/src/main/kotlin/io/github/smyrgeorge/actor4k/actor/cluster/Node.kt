package io.github.smyrgeorge.actor4k.actor.cluster

import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.net.Address

class Node(
    val alias: String,
    val isSeed: Boolean,
    val seedPort: Int,
    val seedMembers: List<Address>,
    val handler: ClusterMessageHandler
) {

    class Builder {
        private var alias: String? = null
        private var isSeed: Boolean? = null
        private var seedPort: Int? = null
        private var seedMembers: List<Address>? = null
        private var handler: ClusterMessageHandler? = null

        fun alias(v: String): Builder {
            alias = v
            return this
        }

        fun isSeed(v: Boolean): Builder {
            isSeed = v
            return this
        }

        fun seedPort(v: Int): Builder {
            seedPort = v
            return this
        }

        fun seedMembers(v: List<Address>): Builder {
            seedMembers = v
            return this
        }

        fun handler(v: ClusterMessageHandler): Builder {
            handler = v
            return this
        }

        fun build(): Node =
            Node(alias!!, isSeed!!, seedPort!!, seedMembers!!, handler!!)
    }
}