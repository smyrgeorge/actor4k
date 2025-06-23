package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ActorProtocol.Response

sealed interface Behavior<Res : Response> {
    class Respond<Res : Response>(val value: Res) : Behavior<Res> {
        fun toResult(): Result<Res> = Result.success(value)
    }

    class Error<Res : Response>(val cause: Throwable) : Behavior<Res> {
        fun toResult(): Result<Res> = Result.failure(cause)
    }

    class Stash<Res : Response> : Behavior<Res>
    class Shutdown<Res : Response> : Behavior<Res>
}