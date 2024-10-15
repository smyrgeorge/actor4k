package io.github.smyrgeorge.actor4k.cluster.raft

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.microraft.RaftEndpoint
import io.microraft.model.message.RaftMessage
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import io.microraft.transport.Transport as RaftTransport

class Transport(private val self: Endpoint) : RaftTransport {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }

    override fun send(target: RaftEndpoint, message: RaftMessage) {
        target as Endpoint

        if (self.alias == target.alias) {
            log.warn("Received message from myself (I wasn't expecting this).")
            runBlocking { cluster.raftManager.send(message) }
            return
        }

        runBlocking {
            launch(Dispatchers.IO) {
                try {
                    val member = cluster.gossip.members().firstOrNull { it.alias() == target.id }
                    if (member != null) {
                        val msg = Message.builder().data(MessageHandler.Protocol.Targeted.RaftProtocol(message)).build()
                        cluster.gossip.send(member, msg).awaitFirstOrNull()
                    } else {
                        log.warn("Could not send ${message::class.simpleName} to ${target.alias}. Seems offline.")
                    }

                } catch (e: Exception) {
                    log.warn("Could not send ${message::class.simpleName} to ${target.alias}. Reason: ${e.message}")
                }
            }
        }
    }

    override fun isReachable(endpoint: RaftEndpoint): Boolean =
        cluster.gossip.members().any { it.alias() == endpoint.id }
}
