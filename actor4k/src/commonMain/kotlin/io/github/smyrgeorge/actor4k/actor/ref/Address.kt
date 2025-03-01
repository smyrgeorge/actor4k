package io.github.smyrgeorge.actor4k.actor.ref

/**
 * Represents a unique address within the actor system, used to identify
 * and reference actors in the system.
 *
 * @property name The name associated with this address.
 * @property key The unique key that distinguishes this address.
 */
data class Address(
    val name: String,
    val key: String
) {

    private val address: String = "$name-$key"
    private val hash: Int = address.hashCode()

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
}
