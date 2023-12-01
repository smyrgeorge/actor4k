package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cluster.Cluster

@Suppress("unused")
object ActorSystem {
    private val log = KotlinLogging.logger {}

    val registry = ActorRegistry
    private var cluster: Cluster? = null

    fun register(c: Cluster): ActorSystem {
        if (cluster != null) error("Cluster already registered.")
        cluster = c
        return this
    }
}