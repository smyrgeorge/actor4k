package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage
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
 * @param node The node in the cluster that hosts the referenced actor.
 * @param service The RPC service used to communicate with the remote actor.
 * @param address The network address of the actor within the cluster.
 */
class ClusterActorRef(
    private val node: ClusterNode,
    private val service: RpcSendService,
    address: Address
) : ActorRef(address) {

    /**
     * Sends a message to the actor identified by the given address.
     *
     * @param msg The message to be sent to the actor.
     */
    override suspend fun tell(msg: Actor.Message): Result<Unit> {
        val res: ClusterMessage.Response = service.tell(address, msg)
        return when (res) {
            is ClusterMessage.Response.Empty -> Result.success(Unit)
            is ClusterMessage.Response.Failure -> {
                val ex: IllegalStateException = if (res.cause != null) {
                    IllegalStateException(res.message, Exception(res.cause))
                } else IllegalStateException(res.message)
                Result.failure(ex)
            }

            else -> Result.failure(IllegalStateException("Unexpected response $res for tell command."))
        }
    }

    /**
     * Sends a message to an actor and waits for a response within a specified timeout duration.
     *
     * @param msg The message to be sent to the actor.
     * @param timeout The duration to wait for a response before timing out.
     * @return A [Result] containing the actor's response of type [Res] if successful, or a failure if an error occurs.
     */
    override suspend fun <Res : Actor.Message.Response> ask(msg: Actor.Message, timeout: Duration): Result<Res> {
        val res: ClusterMessage.Response = service.ask(address, msg)
        return when (res) {
            is ClusterMessage.Response.Success ->
                @Suppress("UNCHECKED_CAST") Result.success(res.response as Res)

            is ClusterMessage.Response.Failure -> {
                val ex: IllegalStateException = if (res.cause != null) {
                    IllegalStateException(res.message, Exception(res.cause))
                } else IllegalStateException(res.message)
                Result.failure(ex)
            }

            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Retrieves the status of the actor associated with the specified address.
     *
     * @return The current status of the actor as an instance of [Actor.Status].
     */
    override suspend fun status(): Actor.Status = service.status(address).status

    /**
     * Retrieves the statistical data of the actor associated with the specified address.
     *
     * @return Statistical information about the actor as an instance of [Actor.Stats].
     */
    override suspend fun stats(): Actor.Stats = service.stats(address).stats

    /**
     * Initiates the shutdown process for the actor associated with the specified address.
     *
     * Delegates the shutdown request to the service to terminate the actor's operations.
     *
     * @return Unit signaling the completion of the shutdown process.
     */
    override suspend fun shutdown(): Unit = service.shutdown(address)

    /**
     * Provides a string representation of the `ClusterActorRef` instance.
     *
     * @return A string that includes the class name and the node and address information in the format "ClusterActorRef(node//address)".
     */
    override fun toString(): String = "ClusterActorRef($node//$address)"
}