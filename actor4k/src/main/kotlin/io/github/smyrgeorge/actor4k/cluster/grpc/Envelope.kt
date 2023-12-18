package io.github.smyrgeorge.actor4k.cluster.grpc

sealed interface Envelope {
    @Suppress("ArrayInDataClass")
    data class Message(
        val payload: ByteArray
    ) : Envelope

    data class Spawn(
        val className: String,
        val key: String
    ) : Envelope
}