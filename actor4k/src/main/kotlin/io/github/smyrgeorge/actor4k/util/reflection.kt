package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

suspend fun Class<*>.callSuspend(method: String, obj: Any): Any? =
    kotlin.memberFunctions.first { it.name == method }.callSuspend(obj)
