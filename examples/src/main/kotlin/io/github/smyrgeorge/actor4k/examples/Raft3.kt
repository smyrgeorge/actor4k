@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.examples.raft.LocalRaftEndpoint
import io.github.smyrgeorge.actor4k.examples.raft.LocalTransport
import io.github.smyrgeorge.actor4k.examples.raft.OperableAtomicRegister
import io.microraft.*
import io.microraft.report.RaftGroupMembers
import io.microraft.statemachine.StateMachine
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * NOTES:
 * If we call RaftNode.replicate() on a follower or candidate Raft node,
 * the returned CompletableFuture<Ordered> object is simply notified with NotLeaderException,
 * which also provides Raft endpoint of the leader Raft node.
 * I am not going to build an advanced RPC system in front of MicroRaft here,
 * but when we use MicroRaft in a distributed setting,
 * we can build a retry mechanism in the RPC layer to forward a failed operation
 * to the Raft endpoint given in the exception.
 * NotLeaderException may not specify any leader as well,
 * for example if there is an ongoing leader election round,
 * or the Raft node we contacted does not know the leader yet.
 * In this case, our RPC layer could retry the operation on each Raft node in a round-robin fashion
 * until it discovers the new leader.
 */
// https://microraft.io/docs/tutorial-building-an-atomic-register/#tutorial-building-an-atomic-register
class Raft3

fun main(args: Array<String>) {
    // Before
    initialMembers.map { createRaftNode(it) }.forEach { it.start() }

    // Block main thread until election is finished (in the back-ground)
    val leader = waitUntilLeaderElected()

    val value1 = "value1"
    leader.replicate<String?>(OperableAtomicRegister.newSetOperation(value1)).join()

    // when we start a new Raft node, we should first add it to the Raft
    // group, then start its RaftNode instance.
    val endpoint4: RaftEndpoint = LocalRaftEndpoint("bank-4")

    // group members commit index of the initial Raft group members is 0.
    val newMemberList1: RaftGroupMembers = leader.changeMembership(
        /* endpoint = */ endpoint4,
        /* mode = */ MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER,
        /* expectedGroupMembersCommitIndex = */ 0
    ).join().result
    println("New member list: ${newMemberList1.members}, majority: ${newMemberList1.majorityQuorumSize}, commit index: ${newMemberList1.logIndex}")


    // endpoint4 is now part of the member list. Let's start its Raft node
    val raftNode4 = createRaftNode(endpoint4)
    raftNode4.start()

    val value2 = "value2"
    leader.replicate<String?>(OperableAtomicRegister.newSetOperation(value2)).join()

    val endpoint5: RaftEndpoint = LocalRaftEndpoint("bank-5")


    // we need the commit index of the current Raft group member list for
    // adding endpoint5. We can get it either from
    // newMemberList1.getCommitIndex() or leader.getCommittedMembers().
    val newMemberList2: RaftGroupMembers = leader.changeMembership(
        /* endpoint = */ endpoint5,
        /* mode = */ MembershipChangeMode.ADD_OR_PROMOTE_TO_FOLLOWER,
        /* expectedGroupMembersCommitIndex = */ newMemberList1.logIndex
    ).join().result
    println("New member list: ${newMemberList2.members}, majority: ${newMemberList2.majorityQuorumSize}, commit index: ${newMemberList2.logIndex}")


    // endpoint5 is also part of the member list now. Let's start its Raft node
    val raftNode5 = createRaftNode(endpoint5)
    raftNode5.start()

    val value3 = "value3"
    leader.replicate<String?>(OperableAtomicRegister.newSetOperation(value3)).join()

    // wait until the new Raft nodes replicate the log entries.
    TimeUnit.SECONDS.sleep(5)

    try {
        println("${endpoint4.id}'s local query result: ${query(leader).result}")
        println("${endpoint5.id}'s local query result: ${query(leader).result}")
    } catch (e: Exception) {
        println(e.message)
    }

    // After
    raftNodes.forEach { it.terminate() }
}


private fun createRaftNode(endpoint: RaftEndpoint): RaftNode {
    val transport = LocalTransport(endpoint)
    val stateMachine: StateMachine = OperableAtomicRegister()
    val raftNode = RaftNode
        .newBuilder()
        .setGroupId("default")
        .setLocalEndpoint(endpoint)
        .setInitialGroupMembers(initialMembers)
        .setRaftNodeReportListener {
            println("XXX: $it")
        }.setTransport(transport)
        .setStateMachine(stateMachine)
        .build()

    raftNodes.add(raftNode)
    transports.add(transport)
    enableDiscovery(raftNode, transport)

    return raftNode
}

private fun query(raftNode: RaftNode): Ordered<String> {
    return raftNode.query<String>(
        /* operation = */ OperableAtomicRegister.newGetOperation(),
        /* queryPolicy = */ QueryPolicy.LEADER_LEASE,
        /* minCommitIndex = */ Optional.of(0),
        /* timeout = */ Optional.empty()
    ).join()
}

