package io.github.smyrgeorge.actor4k.java.util

import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry

/**
 * Converts this `ActorRef` to a `JRef` for Java interoperability.
 *
 * @return a `JRef` instance that wraps this `ActorRef`, allowing interaction from Java code.
 */
fun ActorRef.asJava(): JRef = JRef(this)

/**
 * Converts the current actor registry to a Java-compatible `JActorRegistry` instance.
 *
 * @return A `JActorRegistry` object providing Java interoperability for actor registry functionalities.
 */
fun ActorRegistry.asJava(): JActorRegistry = JActorRegistry