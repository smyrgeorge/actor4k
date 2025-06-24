package io.github.smyrgeorge.actor4k.actor

import io.github.smyrgeorge.actor4k.actor.ActorProtocol.Response

/**
 * Represents a behavior outcome or operation that a system can process.
 *
 * The generic type parameter `Res` extends the `Response` class, representing the response type linked to the behavior.
 * This sealed interface allows modeling behaviors such as successful responses, errors, or operational directives.
 *
 * @param Res The type of response associated with the behavior.
 */
sealed interface Behavior<Res : Response> {
    /**
     * Represents a response behavior wrapping a specific response value.
     *
     * This class implements the `Behavior` sealed interface and encapsulates a concrete
     * response of type `Res`. It provides the ability to convert the response into
     * a successful result.
     *
     * @param Res The type of the response being wrapped.
     * @property value The response instance associated with this behavior.
     */
    class Reply<Res : Response>(val value: Res) : Behavior<Res>

    /**
     * Represents an error behavior associated with a given cause.
     *
     * This class is a part of the `Behavior` structure and encapsulates an exceptional
     * condition that occurred during the execution of an operation. It provides the ability
     * to convey the failure as a result type.
     *
     * @param Res The type of the response associated with the behavior.
     * @property cause The throwable instance representing the error's cause.
     */
    class Error<Res : Response>(val cause: Throwable) : Behavior<Res>

    /**
     * A class representing a behavior that produces no specific actionable result or effect.
     *
     * The `None` behavior is parameterized with a type `Res` that extends the `Response` class.
     * This class can be used as a placeholder or for scenarios where no specific behavior result
     * or operation is required, maintaining type consistency within the system.
     *
     * @param Res Represents the type of response associated with this behavior,
     *            constrained to types that inherit from `Response`.
     */
    class None<Res : Response> : Behavior<Res>

    /**
     * A behavior representing a shutdown operation for entities responding to a protocol.
     *
     * The `Shutdown` behavior is used to define specific actions or responsibilities
     * associated with terminating or halting operations. It operates within the context of
     * responses that conform to the protocol defined by the `Response` class. The generic type
     * parameter `Res` ensures this behavior is tied to a specific type of response.
     *
     * @param Res The type of `Response` this behavior operates on.
     */
    class Shutdown<Res : Response> : Behavior<Res>
}