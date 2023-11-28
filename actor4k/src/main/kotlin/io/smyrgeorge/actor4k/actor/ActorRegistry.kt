package io.smyrgeorge.actor4k.actor

import io.github.oshai.kotlinlogging.KotlinLogging
import io.smyrgeorge.actor4k.actor.cmd.Cmd
import io.smyrgeorge.actor4k.actor.cmd.Reply
import io.smyrgeorge.actor4k.actor.types.ManagedActor
import java.util.concurrent.ConcurrentHashMap

object ActorRegistry {
    private val log = KotlinLogging.logger {}

    private val registry: ConcurrentHashMap<String, ManagedActor<*, *>> =
        ConcurrentHashMap<String, ManagedActor<*, *>>(/* initialCapacity = */ 100)

    fun register(actor: ManagedActor<*, *>) {
        val existing = registry[actor::class.java.name]
        if (existing != null) error("A managed actor ${actor::class.java.name} is already registered.")

        registry[actor::class.java.name] = actor
        log.debug { "${actor::class.java} registered." }
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : Cmd, R : Reply> get(clazz: Class<*>): ManagedActor<C, R> =
        registry[clazz.name] as? ManagedActor<C, R>
            ?: error("Requested ${clazz.name} actor is not registered.")
}