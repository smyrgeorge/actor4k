package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.actor.impl.RouterActor

fun main() {
    @Suppress("UnusedVariable")
    val router = RouterActor().register(RouterActorChild())
}