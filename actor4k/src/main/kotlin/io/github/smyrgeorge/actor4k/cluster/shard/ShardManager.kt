package io.github.smyrgeorge.actor4k.cluster.shard

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smyrgeorge.actor4k.cluster.Cluster
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.cluster.grpc.Envelope
import io.github.smyrgeorge.actor4k.cluster.raft.StateMachine
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.scalecube.cluster.transport.api.Message
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import java.util.concurrent.ConcurrentHashMap

object ShardManager {

    private val log = KotlinLogging.logger {}
    private val shards = ConcurrentHashMap<Shard.Key, Int>()
    private val lockedShards: MutableSet<Shard.Key> = mutableSetOf()

    fun isLocked(shard: Shard.Key): Envelope.Response.Error? {
        if (lockedShards.contains(shard)) {
            return Envelope.Response.Error(
                code = Envelope.Response.Error.Code.ShardError,
                message = "Cannot process message for shard='${shard.value}', shard is locked (due to cluster migration)."
            )
        }

        if (ActorSystem.cluster.nodeOf(shard).dc != ActorSystem.cluster.node.alias) {
            return Envelope.Response.Error(
                code = Envelope.Response.Error.Code.ShardError,
                message = "Message for requested shard='${shard.value}' is not supported for node='${ActorSystem.cluster.node.alias}'."
            )
        }

        return null
    }

    enum class Op {
        REGISTER,
        UNREGISTER
    }

    @Synchronized
    fun operation(op: Op, shard: Shard.Key) =
        when (op) {
            Op.REGISTER -> register(shard)
            Op.UNREGISTER -> unregister(shard)
        }

    private fun register(shard: Shard.Key) {
        val existing: Int? = shards[shard]
        if (existing != null) shards[shard] = existing + 1
        else shards[shard] = 1
    }

    private fun unregister(shard: Shard.Key) {
        when (val existing: Int? = shards[shard]) {
            null, 1 -> {
                shards.remove(shard)

                lockedShards.remove(shard)
                if (lockedShards.isEmpty()) {
                    val self = ActorSystem.cluster
                    self.raft.term.leaderEndpoint?.let {
                        val data = MessageHandler.Protocol.Targeted.ShardedActorsFinished(self.node.alias)
                        val message = Message.builder().data(data).build()
                        val member = self.gossip.members().first { m -> m.alias() == it.id }
                        runBlocking { self.gossip.send(member, message).awaitFirstOrNull() }
                    }
                }

            }

            else -> shards[shard] = existing - 1
        }
    }

    fun requestLockShardsForJoiningNode(node: ServerNode) {
        val self = ActorSystem.cluster

        // The requester has to lock the shards immediately.
        val locked = lockShardsForJoiningNode(node)

        // Update the state.
        if (locked > 0) {
            self.raft.replicate<Unit>(StateMachine.Operation.ShardsLocked(self.node.alias))
        } else {
            self.raft.replicate<Unit>(StateMachine.Operation.ShardedActorsFinished(self.node.alias))
        }

        // Send the lock message to the other nodes.
        val data = MessageHandler.Protocol.Gossip.LockShardsForJoiningNode(node.dc, node.ip, node.port)
        val message = Message.builder().sender(self.gossip.member().address()).data(data).build()
        runBlocking { ActorSystem.cluster.gossip.spreadGossip(message).awaitFirstOrNull() }
    }

    fun requestLockShardsForLeavingNode(node: ServerNode) {
        val self = ActorSystem.cluster

        // The requester has to lock the shards immediately.
        val locked = lockShardsForLeavingNode(node)

        // Update the state.
        if (locked > 0) {
            self.raft.replicate<Unit>(StateMachine.Operation.ShardsLocked(self.node.alias))
        } else {
            self.raft.replicate<Unit>(StateMachine.Operation.ShardedActorsFinished(self.node.alias))
        }

        // Send the lock message to the other nodes.
        val data = MessageHandler.Protocol.Gossip.LockShardsForLeavingNode(node.dc, node.ip, node.port)
        val message = Message.builder().sender(self.gossip.member().address()).data(data).build()
        runBlocking { ActorSystem.cluster.gossip.spreadGossip(message).awaitFirstOrNull() }
    }

    fun lockShardsForJoiningNode(node: ServerNode): Int {
        lockedShards.clear()
        val shards = getMigrationShardsForJoiningNode(node)
        lockedShards.addAll(shards)
        log.info { "Locked ${lockedShards.size} shards." }
        return lockedShards.size
    }

    fun lockShardsForLeavingNode(node: ServerNode): Int {
        lockedShards.clear()
        val shards = getMigrationShardsForLeavingNode(node)
        lockedShards.addAll(shards)
        log.info { "Locked ${lockedShards.size} shards." }
        return lockedShards.size
    }

    private fun getMigrationShardsForJoiningNode(node: ServerNode): Set<Shard.Key> {
        val self = ActorSystem.cluster.node
        val ring = Cluster.hashRingOf(self.namespace).apply {
            // Add existing nodes.
            addAll(ActorSystem.cluster.ring.nodes)
            // Add new node.
            add(node)
        }

        // Find shards that we should migrate to another node.
        return shards.filter { ring.locate(it.key.value).get().dc != self.alias }.map { it.key }.toSet()
    }

    private fun getMigrationShardsForLeavingNode(node: ServerNode): Set<Shard.Key> {
        val self = ActorSystem.cluster.node
        val ring = Cluster.hashRingOf(self.namespace).apply {
            // Add existing nodes.
            addAll(ActorSystem.cluster.ring.nodes)
            // Remove the leaving node.
            remove(node)
        }

        // Find shards that we should migrate to another node.
        return shards.filter { ring.locate(it.key.value).get().dc != self.alias }.map { it.key }.toSet()
    }
}