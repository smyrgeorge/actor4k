@file:OptIn(ExperimentalTime::class)

package io.github.smyrgeorge.actor4k.actor

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Base interface representing the structure of a protocol entity.
 *
 * The `ActorProtocol` interface provides common properties and methods intended to be used
 * across various protocol-related implementations, encompassing core attributes like
 * identification and creation timestamps.
 */
interface ActorProtocol {
    var id: Long
    val createdAt: Long

    /**
     * Determines if the current message is the first one.
     *
     * @return `true` if the message's identifier (`id`) is `1L`, indicating it is the first message; `false` otherwise.
     */
    fun isFirst(): Boolean = id == 1L

    /**
     * Represents an abstract message in the communication protocol.
     *
     * The `Message` class serves as a base structure for defining various types of messages
     * exchanged within a protocol. Each message is associated with a corresponding response type.
     *
     * @param R The type of response associated with this message. Must extend the `Response` class.
     */
    @Serializable
    abstract class Message<R : Response> : ActorProtocol {
        override var id: Long = -1L
        override val createdAt: Long = Clock.System.now().toEpochMilliseconds()
    }

    /**
     * Represents an abstract response in the communication protocol.
     *
     * The `Response` class serves as a base structure for concrete response implementations,
     * providing default properties to manage response identification and creation time.
     *
     * It implements the `Protocol` interface, ensuring compliance with defined protocol standards.
     */
    @Serializable
    abstract class Response : ActorProtocol {
        override var id: Long = -1L
        override val createdAt: Long = Clock.System.now().toEpochMilliseconds()
    }
}