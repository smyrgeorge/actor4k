package io.github.smyrgeorge.actor4k.cluster

import io.github.smyrgeorge.actor4k.actor.Actor
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.Address
import io.github.smyrgeorge.actor4k.cluster.rpc.ClusterMessage
import io.github.smyrgeorge.actor4k.cluster.rpc.RpcSendService
import io.github.smyrgeorge.actor4k.cluster.util.ClusterNode
import kotlin.time.Duration

class ClusterActorRef(
    private val node: ClusterNode,
    private val service: RpcSendService,
    address: Address
) : ActorRef(address) {

    override suspend fun tell(msg: Actor.Message) {
        service.tell(address, msg)
    }

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

    override suspend fun status(): Actor.Status = service.status(address).status
    override suspend fun stats(): Actor.Stats = service.stats(address).stats
    override suspend fun shutdown(): Unit = service.shutdown(address)

    /**
     * Provides a string representation of the `ClusterActorRef` instance.
     *
     * @return A string that includes the class name and the node and address information in the format "ClusterActorRef(node//address)".
     */
    override fun toString(): String = "ClusterActorRef($node//$address)"
}