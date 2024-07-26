package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager

abstract class Stats {

    abstract var actors: Int
    abstract fun collect()

    data class Simple(
        override var actors: Int = 0,
    ) : Stats() {
        override fun collect() {
            actors = ActorRegistry.count()
        }

        override fun toString(): String =
            "[SIMPLE: actors=$actors]"
    }

    data class Cluster(
        override var actors: Int = 0,
        private var nodes: Int = 0,
        private var shards: Int = 0
    ) : Stats() {
        override fun collect() {
            actors = ActorRegistry.count()
            // Set cluster members size.
            nodes = ActorSystem.cluster.ring.size()
            shards = ShardManager.count()
        }

        override fun toString(): String =
            "[CLUSTER: actors=$actors, nodes=$nodes, shards=$shards]"
    }
}
