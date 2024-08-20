package io.github.smyrgeorge.actor4k.cluster

interface Cluster {
    val serde: Serde

    fun start(): Cluster
    fun shutdown()
}
