@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.examples.raft.AtomicRegister
import io.github.smyrgeorge.actor4k.examples.raft.LocalRaftEndpoint
import io.github.smyrgeorge.actor4k.examples.raft.LocalTransport
import io.microraft.RaftEndpoint
import io.microraft.RaftNode
import io.microraft.statemachine.StateMachine
import java.util.concurrent.TimeUnit

// https://microraft.io/docs/tutorial-building-an-atomic-register/#tutorial-building-an-atomic-register
class Raft1

val initialMembers = listOf(LocalRaftEndpoint("bank-1"), LocalRaftEndpoint("bank-2"), LocalRaftEndpoint("bank-3"))
//val initialMembers = listOf(LocalRaftEndpoint("bank-1"))
val transports = mutableListOf<LocalTransport>()
val raftNodes = mutableListOf<RaftNode>()

fun main(args: Array<String>) {
    // Before
    initialMembers
        .map { createRaftNode(it) }
        .forEach { it.start() }

    // Block main thread until election is finished (in the back-ground)
    TimeUnit.SECONDS.sleep(5)

    // After
    raftNodes.forEach { it.terminate() }
}

private fun createRaftNode(endpoint: RaftEndpoint): RaftNode {
    val transport = LocalTransport(endpoint)
    val stateMachine: StateMachine = AtomicRegister()
    val raftNode = RaftNode
        .newBuilder()
        .setGroupId("default")
        .setLocalEndpoint(endpoint)
        .setInitialGroupMembers(initialMembers)
        .setTransport(transport)
        .setStateMachine(stateMachine)
        .build()

    raftNodes.add(raftNode)
    transports.add(transport)
    enableDiscovery(raftNode, transport)

    return raftNode
}

fun enableDiscovery(raftNode: RaftNode, transport: LocalTransport) {
    raftNodes.indices.forEach { i ->
        val otherNode = raftNodes[i]
        if (otherNode !== raftNode) {
            transports[i].discoverNode(raftNode)
            transport.discoverNode(otherNode)
        }
    }
}
