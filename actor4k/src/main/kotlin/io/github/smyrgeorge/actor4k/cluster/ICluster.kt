package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.grpc.Serde

interface ICluster {
    val serde: Serde

    fun start(): ICluster
    fun registerShard(shard: String)
    fun unregisterShard(shard: String)
    fun shardIsLocked(shard: String): Envelope.Response.Error?
    fun shutdown()
}
