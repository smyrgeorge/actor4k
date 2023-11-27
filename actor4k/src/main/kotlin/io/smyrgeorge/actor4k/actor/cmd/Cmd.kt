package io.smyrgeorge.actor4k.actor.cmd

import java.util.UUID

interface Cmd {
    val reqId: UUID
}