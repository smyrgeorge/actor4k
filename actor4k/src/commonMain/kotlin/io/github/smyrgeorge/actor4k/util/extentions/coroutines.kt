package io.github.smyrgeorge.actor4k.util.extentions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

private object EmptyScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}

/**
 * Launches a new coroutine in a default context to execute a given suspendable function.
 *
 * This function is intended to simplify coroutine launching for asynchronous operations.
 * It starts a coroutine in a predefined scope with the default dispatcher.
 *
 * @param f The suspend function to be executed within the launched coroutine.
 * @return A [Job] representing the coroutine, which can be used to monitor and manage its execution.
 */
fun launch(f: suspend () -> Unit): Job =
    EmptyScope.launch(Dispatchers.Default) { f() }

/**
 * Continuously executes a suspendable function `f` at a specified time interval defined by `delay`.
 *
 * This method schedules the given function `f` to run indefinitely, with each execution delayed
 * by the specified duration. If an exception occurs during the execution of `f`, it will be caught
 * and the loop will continue uninterrupted.
 *
 * @param delay The duration to wait between each execution of the provided suspend function.
 * @param f The suspend function to be executed repeatedly.
 */
fun forever(delay: Duration, f: suspend () -> Unit) {
    launch {
        while (true) {
            runCatching {
                delay(delay)
                f()
            }
        }
    }
}
