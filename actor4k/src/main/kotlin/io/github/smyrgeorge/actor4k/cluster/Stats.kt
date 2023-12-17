package io.github.smyrgeorge.actor4k.cluster

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
            while (true) {
                val oldMessages = tM
                val oldGossipMessages = tG
                delay(1_000)
                mPS = tM - oldMessages
                gPs = tG - oldGossipMessages
            }
        }
    }

    fun members(m: Int) {
        members = m
    }

    fun message(): Long = tM++
    fun gossip(): Long = tG++
}