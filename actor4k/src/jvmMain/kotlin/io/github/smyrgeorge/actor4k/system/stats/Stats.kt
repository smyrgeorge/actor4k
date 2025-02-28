package io.github.smyrgeorge.actor4k.system.stats

interface Stats {
    var actors: Int
    fun collect()
}
