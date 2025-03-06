package io.github.smyrgeorge.actor4k.system.stats

import io.github.smyrgeorge.actor4k.system.ActorSystem

data class SimpleStats(
    override var actors: Int = 0,
    var totalMessages: Long = 0,
    var lastCollectPeriodMessages: Long = 0,
) : Stats {
    override fun collect() {
        actors = ActorSystem.registry.size()
        val totalMessages = ActorSystem.registry.totalMessages()
        lastCollectPeriodMessages = totalMessages - this.totalMessages
        if (lastCollectPeriodMessages < 0) lastCollectPeriodMessages = 0
        this.totalMessages = totalMessages
    }

    override fun toString(): String =
        buildString {
            append("[actors=")
            append(actors)
            append(", messages(last ")
            append(ActorSystem.conf.systemCollectStatsEvery)
            append(")=")
            append(lastCollectPeriodMessages)
            append(", total=")
            append(totalMessages)
            append("]")
        }
}
