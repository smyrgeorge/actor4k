package io.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

abstract class AbstractActor<C : Cmd, R : Reply> {

    private val ch = Channel<C>(capacity = Channel.UNLIMITED)

    val log = KotlinLogging.logger {}

    init {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            // For each channel, consume the task requests.
            ch.consumeEach { cmd ->
                log.info { "Received: $cmd" }
            }
        }
    }

    suspend fun tell(cmd: C): Unit = ch.send(cmd)
}