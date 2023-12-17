package io.github.smyrgeorge.actor4k.cluster

import java.io.Serializable
import java.util.*

data class Envelope<T>(
    val reqId: UUID = UUID.randomUUID(),
    val payload: T
) : Serializable
