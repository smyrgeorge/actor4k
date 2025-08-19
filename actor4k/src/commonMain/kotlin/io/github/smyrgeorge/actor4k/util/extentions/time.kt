@file:OptIn(ExperimentalTime::class)

package io.github.smyrgeorge.actor4k.util.extentions

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun instantFromEpochMilliseconds(millis: Long?): Instant? =
    if (millis == null) null else Instant.fromEpochMilliseconds(millis)