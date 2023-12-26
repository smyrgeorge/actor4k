package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit


class ClusterRaftTransport(
    private val self: RaftEndpoint
) : Transport {

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        // TODO: re-think about this.
        if (self == target) {
            error("${self.id} cannot send $message to itself!")
        }

        var retry = true
        var tryCount: Long = 0
        var lastError: Throwable? = null
        // This helps when the cluster is starting (all nodes starting up in parallel).
        while (retry && tryCount < 5) {
            retry = try {
                TimeUnit.SECONDS.sleep(tryCount)
                val member = ActorSystem.cluster.members().first { it.alias() == target.id }
                val msg = Message.builder().data(message).build()
                runBlocking { ActorSystem.cluster.msg(member, msg) }
                false
            } catch (e: NoSuchElementException) {
                lastError = e
                tryCount++
                true
            } catch (e: Exception) {
                lastError = e
                tryCount++
                true
            }
        }

        if (tryCount >= 2) throw lastError!!
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean =
        ActorSystem.cluster.members().any { it.alias() == endpoint.id }
}