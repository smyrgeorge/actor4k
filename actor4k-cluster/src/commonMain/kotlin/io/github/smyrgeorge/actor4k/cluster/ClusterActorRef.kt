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
        val result: Result<ClusterMessage.Response> = service.tell(address, msg)
        val res = if (result.isFailure) return Result.failure(result.exceptionOrNull()!!) else result.getOrThrow()

        return when (res) {
            is ClusterMessage.Response.Empty -> Result.success(Unit)
            is ClusterMessage.Response.Failure -> Result.failure(res.buildException())
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
        val result: Result<ClusterMessage.Response> = service.ask(address, msg)
        val res = if (result.isFailure) return Result.failure(result.exceptionOrNull()!!) else result.getOrThrow()

        return when (res) {
            is ClusterMessage.Response.Success -> @Suppress("UNCHECKED_CAST") Result.success(res.response as Res)
            is ClusterMessage.Response.Failure -> Result.failure(res.buildException())
            else -> Result.failure(IllegalStateException("Unexpected response $res for ask command."))
        }
    }

    /**
     * Retrieves the status of the actor associated with the specified address.
     *
     * @return The current status of the actor as an instance of [Actor.Status].
     */
    override suspend fun status(): Actor.Status {
        val res = service.status(address)
        return when (res) {
            is ClusterMessage.Response.Status -> res.status
            is ClusterMessage.Response.Failure -> throw res.buildException()
            else -> throw IllegalStateException("Unexpected response $res for ask command.")
        }
    }

    /**
     * Retrieves the statistical data of the actor associated with the specified address.
     *
     * @return Statistical information about the actor as an instance of [Actor.Stats].
     */
    override suspend fun stats(): Actor.Stats {
        val res = service.stats(address)
        return when (res) {
            is ClusterMessage.Response.Stats -> res.stats
            is ClusterMessage.Response.Failure -> throw res.buildException()
            else -> throw IllegalStateException("Unexpected response $res for ask command.")
        }
    }

    /**
     * Initiates the shutdown process for the actor associated with the specified address.
     *
     * Delegates the shutdown request to the service to terminate the actor's operations.
     *
     * @return Unit signaling the completion of the shutdown process.
     */
    override suspend fun shutdown() {
        val res = service.shutdown(address)
        when (res) {
            is ClusterMessage.Response.Empty -> Unit
            is ClusterMessage.Response.Failure -> throw res.buildException()
            else -> throw IllegalStateException("Unexpected response $res for ask command.")
        }
    }

    /**
     * Provides a string representation of the `ClusterActorRef` instance.
     *
     * @return A string that includes the class name and the node and address information in the format "ClusterActorRef(node//address)".
     */
    override fun toString(): String = "ClusterActorRef($node//$address)"
}