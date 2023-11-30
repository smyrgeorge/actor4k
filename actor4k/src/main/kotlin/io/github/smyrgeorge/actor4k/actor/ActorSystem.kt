package io.github.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.actor.cluster.Cluster

object ActorSystem {
    private val log = KotlinLogging.logger {}

    val registry = ActorRegistry
    private var cluster: Cluster? = null

    fun cluster(c: Cluster): ActorSystem {
        cluster = c
        return this
    }
}