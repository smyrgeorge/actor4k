package io.github.smyrgeorge.actor4k.cluster

import java.io.Serializable

data class Envelope<T>(
    val payload: T
) : Serializable
