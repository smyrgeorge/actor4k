package io.github.smyrgeorge.actor4k.cluster

@Suppress("unused")
interface Cluster {
    val serde: Serde
    fun start(): Cluster
    fun shutdown()
}
