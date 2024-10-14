package io.github.smyrgeorge.actor4k.util

/**
 * Calculates and returns the amount of free memory available to the JVM.
 *
 * @return The amount of free memory in megabytes.
 */
fun freeMemory(): Long =
    Runtime.getRuntime().freeMemory() / 1024 / 1024

/**
 * Calculates and returns the maximum amount of memory available to the JVM.
 *
 * @return The maximum amount of memory in megabytes.
 */
fun maxMemory(): Long =
    Runtime.getRuntime().maxMemory() / 1024 / 1024