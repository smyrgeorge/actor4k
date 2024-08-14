package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.cluster.Cluster as ClusterImpl

abstract class Stats {

    abstract var actors: Int
    abstract fun collect()

    data class Simple(
        override var actors: Int = 0,
    ) : Stats() {
        override fun collect() {
            actors = ActorSystem.registry.count()
        }

        override fun toString(): String =
            "[actors=$actors]"
    }

    data class Cluster(
        override var actors: Int = 0,
        private var nodes: Int = 0,
        private var shards: Int = 0
    ) : Stats() {
        private val cluster: ClusterImpl = ActorSystem.cluster as ClusterImpl

        override fun collect() {
            actors = ActorSystem.registry.count()
            // Set cluster members size.
            nodes = cluster.ring.size()
            shards = cluster.shardManager.count()
        }

        override fun toString(): String =
            "[actors=$actors, nodes=$nodes, shards=$shards]"
    }
}
