package io.github.smyrgeorge.actor4k.system.stats

abstract class Stats {
    abstract var actors: Int
    abstract fun collect()
}
