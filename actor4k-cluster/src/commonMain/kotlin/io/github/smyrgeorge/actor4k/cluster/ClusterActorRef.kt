package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ActorProtocol
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcEnvelope
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcSendService
import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import kotlin.time.Duration

/**
 * Represents a reference to an actor in a distributed cluster system.
 *
 * This class is responsible for enabling communication with a remote actor residing on a node
 * within a clustered environment. It interacts with the specified node using an RPC (Remote Procedure Call)
 * service to send messages, query actor states, and manage actor lifecycle operations such as shutdown.
 *
 * The `ClusterActorRef` acts as a proxy for the remote actor, abstracting the complexities
 * of network communication and providing a unified interface for interaction.
 *
 * @constructor Creates a `ClusterActorRef` instance.
 * @param address The network address of the actor within the cluster.
 * @param service The RPC service used to communicate with the remote actor.
 */
class ClusterActorRef(
    address: Address,
    private val service: RpcSendService
) : ActorRef(address) {
    private val node: ClusterNode = service.session.node

    /**
     * Sends a message to the actor identified by the given address.
     *
     * @param msg The message to be sent to the actor.
     */
    override suspend fun tell(msg: ActorProtocol): Result<Unit> {
        val res = service.tell(address, msg).getOrElse { return Result.failure(it) }
        return when (res) {
            is RpcEnvelope.Response.Empty -> Result.success(Unit)
            is RpcEnvelope.Response.Failure -> Result.failure(res.exception())
            else -> Result.failure(IllegalStateException("Unexpected response $res for tell command."))
        }
    }

    /**
     * Sends a message to the target actor and waits for a response within a specified timeout.
     *
     * This function ensures that the response corresponds to the sent request. If the response is successfully
     * received and matches the expected type, it returns the result. Otherwise, it handles failure scenarios,
     * such as unexpected response types or exceptions.
     *
     * @param msg The message to be sent to the target actor. Must implement [ActorProtocol.Message] with a corresponding response type.
     * @param timeout The maximum duration to wait for a response before timing out.
     * @return A [Result] containing the successfully received response of type [R], or a failure if an error occurs during
     *         communication, response processing, or timeout.
     */
    override suspend fun <R, M> ask(msg: M, timeout: Duration): Result<R>
            where M : ActorProtocol.Message<R>, R : ActorProtocol.Response {
        val res = service.ask(address, msg).getOrElse { return Result.failure(it) }
        return when (res) {
            is RpcEnvelope.Response.Success -> {
                @Suppress("UNCHECKED_CAST", "SafeCastWithReturn")
                res.response as? R
                    ?: return Result.failure(IllegalStateException("Could not cast ${res.response} to the corresponding type."))
                Result.success(res.response)
            }

            is RpcEnvelope.Response.Failure -> Result.failure(res.exception())
            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Retrieves the current status of the actor.
     *
     * This method sends a request to the actor's service to get its status. Depending on the response,
     * it either returns the status successfully, constructs an exception for a failure response, or raises an
     * error if the response is unexpected.
     *
     * @return A [Result] containing the actor's [Actor.Status] if the operation is successful, or a failure
     * if an error occurs.
     */
    override suspend fun status(): Result<Actor.Status> {
        val res = service.status(address).getOrElse { return Result.failure(it) }
        return when (res) {
            is RpcEnvelope.Response.Status -> Result.success(res.status)
            is RpcEnvelope.Response.Failure -> Result.failure(res.exception())
            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Retrieves statistical information about the actor.
     *
     * Sends a request to the actor's service to collect statistical data. The response is processed to determine
     * success, failure, or unexpected scenarios. On success, it returns the actor's statistics; on failure, it
     * constructs and returns an appropriate exception.
     *
     * @return A [Result] containing the actor's [Actor.Stats] if the operation succeeds, or a failure
     * if an error occurs during the execution.
     */
    override suspend fun stats(): Result<Actor.Stats> {
        val res = service.stats(address).getOrElse { return Result.failure(it) }
        return when (res) {
            is RpcEnvelope.Response.Stats -> Result.success(res.stats)
            is RpcEnvelope.Response.Failure -> Result.failure(res.exception())
            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Initiates a shutdown operation for the associated actor, sending a request to the actor's service
     * and processing the response to determine the outcome of the operation.
     *
     * @return A [Result] wrapping [Unit] upon successful shutdown, or a failure if an error occurs during the process.
     * This includes exceptions caused by unexpected response types or errors reported by the actor's service.
     */
    override suspend fun shutdown(): Result<Unit> {
        val res = service.shutdown(address).getOrElse { return Result.failure(it) }
        return when (res) {
            is RpcEnvelope.Response.Empty -> Result.success(Unit)
            is RpcEnvelope.Response.Failure -> Result.failure(res.exception())
            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Provides a string representation of the `ClusterActorRef` instance.
     *
     * @return A string that includes the class name and the node and address information in the format "ClusterActorRef(node//address)".
     */
    override fun toString(): String = "ClusterActorRef($node//$address)"
}