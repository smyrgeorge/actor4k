package io.github.smyrgeorge.actor4k.system.stats

import io.github.smyrgeorge.actor4k.system.ActorSystem

class SimpleStats : Stats {
    override var actors: Int = 0
    private var totalMessages: Long = 0
    private var currentPeriodMessages: Long = 0

    override suspend fun collect() {
        actors = ActorSystem.registry.size()
        val totalMessages = ActorSystem.registry.totalMessages()
        currentPeriodMessages = totalMessages - this.totalMessages
        if (currentPeriodMessages < 0) currentPeriodMessages = 0
        this.totalMessages = totalMessages
    }

    override fun toString(): String {
        val every = ActorSystem.conf.systemCollectStatsEvery
        val messages = currentPeriodMessages
        val rps = (currentPeriodMessages * 1000) / every.inWholeMilliseconds
        return "[actors=$actors, messages-last-${every.inWholeSeconds}-seconds=$messages ($rps req/sec)]"
    }
}
