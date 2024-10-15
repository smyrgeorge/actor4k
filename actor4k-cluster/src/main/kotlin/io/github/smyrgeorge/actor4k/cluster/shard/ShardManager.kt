package io.github.smyrgeorge.actor4k.cluster.shard

import io.github.smyrgeorge.actor4k.cluster.ClusterImpl
import io.github.smyrgeorge.actor4k.cluster.gossip.MessageHandler
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.util.retryBlocking
import io.scalecube.cluster.transport.api.Message
import io.scalecube.net.Address
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.ishugaliy.allgood.consistent.hash.node.ServerNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Suppress("LoggingSimilarMessage")
class ShardManager {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val cluster: ClusterImpl by lazy {
        ActorSystem.cluster as ClusterImpl
    }

    private var status: Status = Status.OK
    private val shards = ConcurrentHashMap<String, Int>()

    private val shardsBeingMigrated: MutableSet<String> = mutableSetOf()
    private val closedShardsAfterSharadMigrationRequest: MutableSet<String> = mutableSetOf()

    fun isLocked(shard: String): ClusterImpl.Error? {
        if (shardsBeingMigrated.contains(shard)) {
            return ClusterImpl.Error(
                code = ClusterImpl.Error.Code.SHARD_ACCESS_ERROR,
                message = "Cannot process message for shard='$shard', shard is locked due to cluster migration."
            )
        }

        if (cluster.nodeOf(shard).dc != cluster.conf.alias) {
            return ClusterImpl.Error(
                code = ClusterImpl.Error.Code.SHARD_ACCESS_ERROR,
                message = "Message for requested shard='$shard' is not supported for node='${cluster.conf.alias}'."
            )
        }

        return null
    }

    fun count(): Int = shards.size

    enum class Op {
        REGISTER,
        UNREGISTER
    }

    @Synchronized
    fun operation(op: Op, shard: String) =
        when (op) {
            Op.REGISTER -> register(shard)
            Op.UNREGISTER -> unregister(shard)
        }

    private fun register(shard: String) {
        val existing: Int? = shards[shard]
        if (existing != null) shards[shard] = existing + 1
        else shards[shard] = 1
    }

    private fun unregister(shard: String) {
        when (val existing: Int? = shards[shard]) {
            null, 1 -> {
                shards.remove(shard)

                if (status == Status.SHARD_MIGRATION) {
                    closedShardsAfterSharadMigrationRequest.add(shard)
                    if (shardsBeingMigrated == closedShardsAfterSharadMigrationRequest) {
                        // Locked shards are empty at the level.
                        val self = cluster
                        self.raftManager.leader()?.let {
                            val data = MessageHandler.Protocol.Targeted.ShardedActorsFinished(self.conf.alias)
                            val message = Message.builder().data(data).build()
                            val member = self.gossip.members().first { m -> m.alias() == it.id }
                            runBlocking { self.gossip.send(member, message).awaitFirstOrNull() }
                        }
                    }
                }

            }

            else -> shards[shard] = existing - 1
        }
    }

    fun requestUnlockAShards() {
        val self = cluster

        unlockShards()

        // Send the lock message to the other nodes.
        val data = MessageHandler.Protocol.Gossip.UnlockShards
        val message = Message.builder().sender(self.gossip.member().address()).data(data).build()
        retryBlocking { cluster.gossip.spreadGossip(message).awaitFirstOrNull() }
    }

    fun unlockShards() {
        shardsBeingMigrated.clear()
        closedShardsAfterSharadMigrationRequest.clear()
        status = Status.OK
    }

    fun requestLockShardsForJoiningNode(node: ServerNode) {
        if (status == Status.SHARD_MIGRATION)
            error("Could request lock shards. A shard migration is already in progress.")

        val self = cluster

        // The requester has to lock the shards immediately.
        val locked = lockShardsForJoiningNode(node)

        // Update the state.
        if (locked > 0) {
            retryBlocking { self.raftManager.send(MessageHandler.Protocol.Targeted.ShardsLocked(self.conf.alias)) }
        } else {
            retryBlocking { self.raftManager.send(MessageHandler.Protocol.Targeted.ShardedActorsFinished(self.conf.alias)) }
        }

        // Send the lock message to the other nodes.
        val data = MessageHandler.Protocol.Gossip.LockShardsForJoiningNode(node.dc, node.ip, node.port)
        val message = Message.builder().sender(self.gossip.member().address()).data(data).build()
        retryBlocking { cluster.gossip.spreadGossip(message).awaitFirstOrNull() }
    }

    fun requestLockShardsForLeavingNode(node: ServerNode) {
        if (status == Status.SHARD_MIGRATION)
            error("Could request lock shards. A shard migration is already in progress.")

        val self = cluster

        // The requester has to lock the shards immediately.
        val locked = lockShardsForLeavingNode(node)

        // Update the state.
        if (locked > 0) {
            retryBlocking { self.raftManager.send(MessageHandler.Protocol.Targeted.ShardsLocked(self.conf.alias)) }
        } else {
            retryBlocking { self.raftManager.send(MessageHandler.Protocol.Targeted.ShardedActorsFinished(self.conf.alias)) }
        }

        // Send the lock message to the other nodes.
        val data = MessageHandler.Protocol.Gossip.LockShardsForLeavingNode(node.dc, node.ip, node.port)
        val message = Message.builder().sender(self.gossip.member().address()).data(data).build()
        retryBlocking { cluster.gossip.spreadGossip(message).awaitFirstOrNull() }
    }

    fun lockShardsForJoiningNode(sender: Address, data: MessageHandler.Protocol.Gossip.LockShardsForJoiningNode) {
        val self = cluster

        // Only respond if this node is part of the network.
        if (self.ring.nodes.none { it.dc == self.conf.alias }) return

        // Lock the shards.
        val node = ServerNode(data.alias, data.host, data.port)
        val locked = lockShardsForJoiningNode(node)
        // Inform the leader
        informLeaderForTheLockedShards(sender, locked)
    }

    fun lockShardsForLeavingNode(sender: Address, data: MessageHandler.Protocol.Gossip.LockShardsForLeavingNode) {
        val self = cluster

        // Only respond if this node is part of the network.
        if (self.ring.nodes.none { it.dc == self.conf.alias }) return

        // Lock the shards.
        val node = ServerNode(data.alias, data.host, data.port)
        val locked = lockShardsForLeavingNode(node)
        // Inform the leader
        informLeaderForTheLockedShards(sender, locked)
    }

    private fun lockShardsForJoiningNode(node: ServerNode): Int {
        status = Status.SHARD_MIGRATION
        shardsBeingMigrated.clear()
        closedShardsAfterSharadMigrationRequest.clear()
        val shards = getMigrationShardsForJoiningNode(node)
        shardsBeingMigrated.addAll(shards)
        log.info("Locked ${shardsBeingMigrated.size} shards.")
        return shardsBeingMigrated.size
    }

    private fun lockShardsForLeavingNode(node: ServerNode): Int {
        status = Status.SHARD_MIGRATION
        shardsBeingMigrated.clear()
        closedShardsAfterSharadMigrationRequest.clear()
        val shards = getMigrationShardsForLeavingNode(node)
        shardsBeingMigrated.addAll(shards)
        log.info("Locked ${shardsBeingMigrated.size} shards.")
        return shardsBeingMigrated.size
    }

    private fun getMigrationShardsForJoiningNode(node: ServerNode): Set<String> {
        val self = cluster.conf
        val ring = ClusterImpl.hashRingOf(self.namespace).apply {
            // Add existing nodes.
            addAll(cluster.ring.nodes)
            // Add new node.
            add(node)
        }
        // Find shards that we should migrate to another node.
        return shards.filter { ring.locate(it.key).get().dc != self.alias }.map { it.key }.toSet()
    }

    private fun getMigrationShardsForLeavingNode(node: ServerNode): Set<String> {
        val self = cluster.conf
        val ring = ClusterImpl.hashRingOf(self.namespace).apply {
            // Add existing nodes.
            addAll(cluster.ring.nodes)
            // Remove the leaving node.
            remove(node)
        }
        // Find shards that we should migrate to another node.
        return shards.filter { ring.locate(it.key).get().dc != self.alias }.map { it.key }.toSet()
    }

    private fun informLeaderForTheLockedShards(sender: Address, locked: Int) {
        val self = cluster
        val message = if (locked > 0) {
            Message.builder().data(MessageHandler.Protocol.Targeted.ShardsLocked(self.conf.alias)).build()
        } else {
            Message.builder().data(MessageHandler.Protocol.Targeted.ShardedActorsFinished(self.conf.alias)).build()
        }
        retryBlocking { self.gossip.send(sender, message).awaitFirstOrNull() }
    }

    private enum class Status {
        OK,
        SHARD_MIGRATION
    }
}
