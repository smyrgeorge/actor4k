package io.github.smyrgeorge.actor4k.system

import io.github.smyrgeorge.actor4k.cluster.Cluster
import java.time.Duration

object ActorSystem {
    val conf = Conf()
    var clusterMode: Boolean = false
    lateinit var cluster: Cluster

    fun register(c: Cluster): ActorSystem {
        clusterMode = true
        cluster = c
        return this
    }

    data class Conf(
        val clusterLogStats: Duration = Duration.ofSeconds(10),
        val registryCleanup: Duration = Duration.ofSeconds(60),
        val actorExpiration: Duration = Duration.ofMinutes(15)
    )
}