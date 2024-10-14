package io.github.smyrgeorge.actor4k.cluster.system.stats

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.stats.Stats

data class ClusterStats(
    override var actors: Int = 0,
    private var nodes: Int = 0,
    private var shards: Int = 0
) : Stats {
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }

    override fun collect() {
        actors = ActorSystem.registry.count()
        // Set cluster members size.
        nodes = cluster.ring.size()
        shards = cluster.shardManager.count()
    }

    override fun toString(): String =
        "[actors=$actors, nodes=$nodes, shards=$shards]"
}
