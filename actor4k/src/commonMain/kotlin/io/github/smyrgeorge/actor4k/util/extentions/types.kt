package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.actor.Actor
import kotlin.reflect.KClass

typealias AnyActor = Actor<*, *>
typealias AnyActorClass = KClass<out AnyActor>
typealias ActorFactory = (key: String) -> AnyActor
