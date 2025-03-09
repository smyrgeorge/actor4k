package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.actor.Actor

internal typealias AnyActor = Actor<*, *>
internal typealias ActorFactory = (key: String) -> AnyActor