package io.github.smyrgeorge.actor4k.util

import io.scalecube.net.Address

fun addressOf(value: String): Pair<String, Address> =
    value.split("::").let { it[0] to Address.from(it[1]) }