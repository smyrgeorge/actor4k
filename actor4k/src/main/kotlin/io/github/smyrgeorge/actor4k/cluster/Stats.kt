package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*

data class Stats(
    private var members: Int = 0,
    private var tG: Long = 0,
    private var gPs: Long = 0,
    private var tM: Long = 0,
    private var mPS: Long = 0,
) {

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            // Delay first calculation until the system warms up.
            delay(15_000)
            while (true) calculate()
        }
    }

    private suspend fun calculate() {
        // Set cluster members size.
        members = ActorSystem.cluster.members().size

        // Calculate messages per second.
        val oldMessages = tM
        val oldGossipMessages = tG
        delay(1_000)
        mPS = tM - oldMessages
        gPs = tG - oldGossipMessages
    }

    fun message(): Long = tM++
    fun gossip(): Long = tG++
}