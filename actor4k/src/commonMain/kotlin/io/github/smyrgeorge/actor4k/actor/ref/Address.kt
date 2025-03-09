package io.github.smyrgeorge.actor4k.actor.ref

import io.github.smyrgeorge.actor4k.util.extentions.AnyActorClass
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

/**
 * Represents a unique address within the actor system, used to identify
 * and reference actors in the system.
 *
 * @property name The name associated with this address.
 * @property key The unique key that distinguishes this address.
 */
@Serializable
data class Address(
    val name: String,
    val key: String
) {

    val keyHash: Int by lazy { key.hashCode().absoluteValue }
    private val address: String by lazy { "$name-$key" }
    private val hash: Int by lazy { address.hashCode() }

    /**
     * Returns the string representation of the address.
     *
     * @return The address as a concatenated string of the name and key in the format "name-key".
     */
    override fun toString(): String = address

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Address

        if (name != other.name) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int = hash

    companion object {
        /**
         * Retrieves the name of the given actor class.
         *
         * @param actor The class of the actor whose name is to be retrieved.
         * @return The simple name of the actor's class, or "Anonymous" if the name is not available.
         */
        private fun nameOf(actor: AnyActorClass): String = actor.simpleName ?: "Anonymous"

        /**
         * Computes the address of an actor based on its class type and a unique key.
         *
         * @param actor The class type of the actor.
         * @param key A unique key that identifies the actor.
         * @return The computed address of the actor as an [Address] object.
         */
        fun of(actor: AnyActorClass, key: String): Address = Address(nameOf(actor), key)
    }
}
