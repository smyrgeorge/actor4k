package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ActorProtocol.Response

sealed interface Behavior<Res : Response> {
    class Respond<Res : Response>(val value: Res) : Behavior<Res>
    class Error<Res : Response>(val cause: Throwable) : Behavior<Res>
    class Stash<Res : Response> : Behavior<Res>
    class Shutdown<Res : Response> : Behavior<Res>
}