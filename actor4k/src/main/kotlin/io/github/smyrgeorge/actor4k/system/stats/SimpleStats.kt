package io.github.smyrgeorge.actor4k.system.stats

import io.github.smyrgeorge.actor4k.system.ActorSystem

data class SimpleStats(
    var totalMessages: Long = 0,
    override var actors: Int = 0,
    var lastCollectPeriodMessages: Long = 0,
) : Stats() {
    override fun collect() {
        actors = ActorSystem.registry.count()
        val totalMessages = ActorSystem.registry.totalMessages()
        lastCollectPeriodMessages = totalMessages - this.totalMessages
        this.totalMessages = totalMessages
    }

    override fun toString(): String =
        buildString {
            append("[actors=")
            append(actors)
            append(", messages(last ")
            append(ActorSystem.conf.clusterCollectStats)
            append(")=")
            append(lastCollectPeriodMessages)
            append(", total=")
            append(totalMessages)
            append("]")
        }
}