package io.github.smyrgeorge.actor4k.actor.cluster

import io.scalecube.cluster.Cluster as ScaleCluster

class Cluster {

    private lateinit var cluster: ScaleCluster
    private lateinit var node: Node

    fun register(n: Node): Cluster {
        node = n
        cluster = n.build()
        return this
    }

}