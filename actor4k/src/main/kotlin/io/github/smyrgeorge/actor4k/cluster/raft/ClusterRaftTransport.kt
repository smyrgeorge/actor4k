package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.microraft.transport.Transport
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking


class ClusterRaftTransport(private val self: ClusterRaftEndpoint) : Transport {

    private val log = KotlinLogging.logger {}

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        target as ClusterRaftEndpoint

        if (self.alias == target.alias) {
            ActorSystem.cluster.raft.handle(message)
            return
        }

        runBlocking {
            launch(Dispatchers.IO) {
                try {
                    val member = ActorSystem.cluster.gossip.members().firstOrNull { it.alias() == target.id }
                    if (member != null) {
                        val msg = Message.builder().data(MessageHandler.Protocol.RaftProtocol(message)).build()
                        ActorSystem.cluster.gossip.send(member, msg).awaitFirstOrNull()
                    } else {
                        log.warn { "Could not send ${message::class.simpleName} to ${target.alias}. Seems offline." }
                    }

                } catch (e: Exception) {
                    log.warn { "Could not send ${message::class.simpleName} to ${target.alias}. Reason: ${e.message}" }
                }
            }
        }
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean =
        ActorSystem.cluster.gossip.members().any { it.alias() == endpoint.id }
}