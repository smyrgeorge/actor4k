package io.github.smyrgeorge.actor4k.cluster.gossip

import java.io.Serializable

data class Metadata(val grpcPort: Int) : Serializable
