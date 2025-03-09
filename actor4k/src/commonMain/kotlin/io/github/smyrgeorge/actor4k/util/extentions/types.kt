package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.actor.Actor
import kotlin.reflect.KClass

internal typealias AnyActor = Actor<*, *>
internal typealias AnyActorClass = KClass<out AnyActor>
internal typealias ActorFactory = (key: String) -> AnyActor
