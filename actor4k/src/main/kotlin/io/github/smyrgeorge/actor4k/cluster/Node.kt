package io.github.smyrgeorge.actor4k.cluster

import io.scalecube.cluster.membership.MembershipEvent
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address

class Node(
    val alias: String,
    val namespace: String,
    val isSeed: Boolean,
    val seedPort: Int,
    val grpcPort: Int,
    val seedMembers: List<Address>,
    val onGossip: (g: Message) -> Unit,
    val onMessage: (g: Message) -> Unit,
    val onMembershipEvent: (e: MembershipEvent) -> Unit
) {

    class Builder {
        private lateinit var alias: String
        private lateinit var namespace: String
        private var isSeed: Boolean = false
        private var seedPort: Int = 61100
        private var grpcPort: Int = 50051
        private var seedMembers: List<Address> = emptyList()
        private var onGossip: (m: Message) -> Unit = {}
        private var onMessage: (m: Message) -> Unit = {}
        private var onMembershipEvent: (m: MembershipEvent) -> Unit = {}

        fun alias(v: String): Builder {
            alias = v
            return this
        }

        fun namespace(v: String): Builder {
            namespace = v
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

        fun grpcPort(v: Int): Builder {
            grpcPort = v
            return this
        }

        fun seedMembers(v: List<Address>): Builder {
            seedMembers = v
            return this
        }

        fun onGossip(f: (g: Message) -> Unit): Builder {
            onGossip = f
            return this
        }

        fun onMessage(f: (g: Message) -> Unit): Builder {
            onMessage = f
            return this
        }

        fun onMembershipEvent(f: (e: MembershipEvent) -> Unit): Builder {
            onMembershipEvent = f
            return this
        }

        fun build(): Node = Node(
            alias = alias,
            namespace = namespace,
            isSeed = isSeed,
            seedPort = seedPort,
            grpcPort = grpcPort,
            seedMembers = seedMembers,
            onGossip = onGossip,
            onMessage = onMessage,
            onMembershipEvent = onMembershipEvent
        )
    }
}