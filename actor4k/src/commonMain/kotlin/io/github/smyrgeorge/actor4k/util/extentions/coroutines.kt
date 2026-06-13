package io.github.smyrgeorge.actor4k.util.extentions

import io.github.smyrgeorge.actor4k.system.ActorSystem
import kotlinx.coroutines.*
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
 * Executes a given suspendable function repeatedly with a specified delay between invocations.
 *
 * @param delay The time interval between each execution of the provided suspendable function.
 * @param dispatcher The coroutine dispatcher on which the coroutine will be launched. Defaults to `ActorSystem.dispatcher`.
 * @param f The suspendable function to be executed repeatedly.
 * @return A `Job` representing the lifecycle of the coroutine performing the repeated executions.
 */
fun doEvery(
    delay: Duration,
    dispatcher: CoroutineDispatcher = ActorSystem.dispatcher,
    f: suspend () -> Unit
): Job {
    return launch(dispatcher) {
        while (true) {
            // `delay` is outside the catch so a CancellationException from cancelling the returned
            // Job propagates and stops the loop. Catching it turns cancel() into a hot 100%-CPU spin,
            // since every subsequent delay() on a cancelled job throws.
            delay(delay)
            try {
                f()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Swallow errors from the action so the periodic loop keeps ticking.
            }
        }
    }
}
