package io.smyrgeorge.actor4k.actor.cluster

import io.scalecube.cluster.ClusterImpl
import io.scalecube.cluster.ClusterMessageHandler
import io.scalecube.net.Address
import io.scalecube.transport.netty.tcp.TcpTransportFactory
import io.scalecube.cluster.Cluster as ScaleCluster

class Node(
    private val alias: String,
    private val isSeed: Boolean,
    private val seedMembers: List<Address>,
) {

    private lateinit var handler: ClusterMessageHandler

    fun handler(h: ClusterMessageHandler): Node {
        handler = h
        return this
    }

    fun build(): ScaleCluster = clusterOf()

    private fun clusterOf(): ScaleCluster {
        val c: ClusterImpl = if (isSeed) seedOf() else nodeOf()

        return c
            .handler { handler }
            .transportFactory { TcpTransportFactory() }
            .startAwait()
    }

    private fun seedOf(): ClusterImpl =
        ClusterImpl()
            .config { it.memberAlias(alias) }

    private fun nodeOf(): ClusterImpl =
        ClusterImpl()
            .config { it.memberAlias(alias) }
            .membership { it.seedMembers(seedMembers) }

}