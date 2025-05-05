package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
 * Launches a coroutine within the context of the provided dispatcher or a default dispatcher.
 *
 * This function starts a new coroutine and executes the given suspendable function `f`. It uses
 * a specified coroutine dispatcher or defaults to `ActorSystem.dispatcher` if none is provided.
 *
 * @param dispatcher The coroutine dispatcher to be used for launching the coroutine. Defaults to `ActorSystem.dispatcher`.
 * @param f The suspendable function to be executed within the launched coroutine.
 * @return A `Job` representing the coroutine's lifecycle.
 */
fun launch(
    dispatcher: CoroutineDispatcher = ActorSystem.dispatcher,
    f: suspend () -> Unit
): Job = EmptyScope.launch(dispatcher) { f() }

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
fun forever(delay: Duration, f: suspend () -> Unit): Job {
    return launch {
        while (true) {
            runCatching {
                delay(delay)
                f()
            }
        }
    }
}
