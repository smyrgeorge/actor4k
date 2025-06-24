package io.github.smyrgeorge.actor4k.test.actor

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.Behavior
import io.github.smyrgeorge.actor4k.test.actor.StashingActor.Protocol

/**
 * An actor that demonstrates the stashing mechanism.
 * It stashes messages when in "stashing" mode and processes them when switched to "processing" mode.
 */
class StashingActor(key: String) : Actor<Protocol, Protocol.Response>(key) {
    // Mode determines whether to stash messages or process them
    private var mode: Mode = Mode.STASHING

    // Counter to track processed messages
    private var processedCount: Int = 0

    // List to track the order of processed messages
    private val processedMessages = mutableListOf<String>()

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Behavior<Protocol.Response> {
        log.info("[${address()}] onReceive: $m")

        return when (m) {
            is Protocol.Req -> {
                when {
                    // Switch to processing mode and unstash all messages
                    m.message == "switch_mode" -> {
                        mode = if (mode == Mode.STASHING) {
                            Mode.PROCESSING
                        } else {
                            Mode.STASHING
                        }

                        if (mode == Mode.PROCESSING) {
                            unstashAll()
                        }

                        Behavior.Reply(Protocol.Resp("Switched to $mode mode"))
                    }

                    // Get the current mode
                    m.message == "get_mode" -> {
                        Behavior.Reply(Protocol.Resp("Current mode: $mode"))
                    }

                    // Get the processed count
                    m.message == "get_processed_count" -> {
                        Behavior.Reply(Protocol.Resp("Processed count: $processedCount"))
                    }

                    // Get the processed messages
                    m.message == "get_processed_messages" -> {
                        Behavior.Reply(Protocol.ProcessedMessages(processedMessages.toList()))
                    }

                    // In stashing mode, stash the message
                    mode == Mode.STASHING -> {
                        log.info("[${address()}] Stashing message: ${m.message}")
                        Behavior.Stash()
                    }

                    // In processing mode, process the message
                    else -> {
                        log.info("[${address()}] Processing message: ${m.message}")
                        processedCount++
                        processedMessages.add(m.message)
                        Behavior.Reply(Protocol.Resp("Processed: ${m.message}"))
                    }
                }
            }
        }
    }

    enum class Mode {
        STASHING,
        PROCESSING
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Req(val message: String) : Message<Response>()
        data class Resp(val message: String) : Response()
        data class ProcessedMessages(val messages: List<String>) : Response()
    }
}
