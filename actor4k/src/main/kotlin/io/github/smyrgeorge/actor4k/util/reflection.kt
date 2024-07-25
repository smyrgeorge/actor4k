package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

suspend fun Any.callSuspend(method: String, vararg args: Any?): Any? =
    this::class.memberFunctions.first { it.name == method }.callSuspend(this, *args)
