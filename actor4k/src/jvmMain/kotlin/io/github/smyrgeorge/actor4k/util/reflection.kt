package io.github.smyrgeorge.actor4k.util

import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions

suspend fun Class<*>.callSuspend(obj: Any, method: String, vararg args: Any?): Any? =
    kotlin.callSuspend(obj, method, *args)

suspend fun KClass<*>.callSuspend(obj: Any, method: String, vararg args: Any?): Any? =
    memberFunctions.first { it.name == method }.callSuspend(obj, *args)
