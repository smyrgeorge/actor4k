package io.github.smyrgeorge.actor4k.examples

import io.scalecube.cluster.ClusterConfig
import io.scalecube.cluster.ClusterImpl
import io.scalecube.transport.netty.tcp.TcpTransportFactory

class Cluster

fun main(args: Array<String>) {

    // Start seed member Alice
    val alice = ClusterImpl()
        .config { opts: ClusterConfig -> opts.memberAlias("Alice") }
        .transportFactory { TcpTransportFactory() }
        .startAwait()
    println("Alice join members: " + alice.members())

    // Start seed member Bob
    val bob = ClusterImpl()
        .config { opts: ClusterConfig -> opts.memberAlias("Bob") }
        .membership { opts -> opts.seedMembers(alice.addresses()) }
        .transportFactory { TcpTransportFactory() }
        .startAwait()
    println("Bob join members: " + bob.members())
}