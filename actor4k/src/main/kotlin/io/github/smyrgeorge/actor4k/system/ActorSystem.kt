package io.github.smyrgeorge.actor4k.system

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import kotlin.math.truncate

@Suppress("unused")
object ActorSystem {
    private val log = KotlinLogging.logger {}

    val registry = ActorRegistry

    var clusterMode: Boolean = false
    lateinit var cluster: Cluster

    fun register(c: Cluster): ActorSystem {
        clusterMode = true
        cluster = c
        return this
    }
}