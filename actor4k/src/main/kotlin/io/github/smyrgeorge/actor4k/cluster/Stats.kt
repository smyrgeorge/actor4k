package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.cluster.shard.ShardManager
import io.github.smyrgeorge.actor4k.system.ActorRegistry
import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*

data class Stats(
    private var members: Int = 0,
    private var shards: Int = 0,
    private var actors: Int = 0
) {

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(ActorSystem.Conf.clusterCollectStats)
                collect()
            }
        }
    }

    private fun collect() {
        // Set cluster members size.
        members = ActorSystem.cluster.ring.size()
        shards = ShardManager.count()
        actors = ActorRegistry.count()
    }
}