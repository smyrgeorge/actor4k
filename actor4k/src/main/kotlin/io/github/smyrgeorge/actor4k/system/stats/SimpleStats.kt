package io.github.smyrgeorge.actor4k.system.stats

import io.github.smyrgeorge.actor4k.system.ActorSystem

data class SimpleStats(
    override var actors: Int = 0
) : Stats() {
    override fun collect() {
        actors = ActorSystem.registry.count()
    }

    override fun toString(): String =
        "[actors=$actors]"
}
