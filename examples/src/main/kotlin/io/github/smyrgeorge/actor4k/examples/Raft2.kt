@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.examples.raft.LocalTransport
import io.github.smyrgeorge.actor4k.examples.raft.OperableAtomicRegister
import io.microraft.Ordered
import io.microraft.RaftEndpoint
import io.microraft.RaftNode
import io.microraft.RaftNodeStatus
import io.microraft.statemachine.StateMachine
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
class Raft2

fun main(args: Array<String>) {
    // Before
    initialMembers.map { createRaftNode(it) }.forEach { it.start() }

    // Block main thread until election is finished (in the back-ground)
    val leader = waitUntilLeaderElected()

    val value1 = "value1"
    val result1: Ordered<String> = leader.replicate<String?>(OperableAtomicRegister.newSetOperation(value1)).join()

//    assertThat(result1.commitIndex).isGreaterThan(0)
//    assertThat(result1.result).isNull()

    println("1st operation commit index: ${result1.commitIndex}, result: ${result1.result}")

    val value2 = "value2"
    val result2: Ordered<String> = leader.replicate<String?>(OperableAtomicRegister.newSetOperation(value2)).join()

//    assertThat(result2.commitIndex).isGreaterThan(result1.commitIndex)
//    assertThat(result2.result).isEqualTo(value1)

    println("2nd operation commit index: ${result2.commitIndex}, result: ${result2.result}")

    val value3 = "value3"
    val result3: Ordered<Boolean> =
        leader.replicate<Boolean?>(OperableAtomicRegister.newCasOperation(value2, value3)).join()

//    assertThat(result3.commitIndex).isGreaterThan(result2.commitIndex)
//    assertThat(result3.result).isTrue()

    println("3rd operation commit index: ${result2.commitIndex}, result: ${result3.result}")

    val value4 = "value4"
    val result4: Ordered<Boolean> =
        leader.replicate<Boolean?>(OperableAtomicRegister.newCasOperation(value2, value4)).join()

//    assertThat(result4.commitIndex).isGreaterThan(result3.commitIndex)
//    assertThat(result4.result).isFalse()

    println("4th operation commit index: ${result4.commitIndex}, result: ${result4.result}")

    val result5: Ordered<String> = leader.replicate<String?>(OperableAtomicRegister.newGetOperation()).join()

//    assertThat(result5.commitIndex).isGreaterThan(result4.commitIndex)
//    assertThat(result5.result).isEqualTo(value3)

    println("5th operation commit index: ${result5.commitIndex}, result: ${result5.result}")

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
        }
        .setTransport(transport)
        .setStateMachine(stateMachine)
        .build()

    raftNodes.add(raftNode)
    transports.add(transport)
    enableDiscovery(raftNode, transport)

    return raftNode
}

fun waitUntilLeaderElected(): RaftNode {
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60)
    while (System.currentTimeMillis() < deadline) {
        val leaderEndpoint: RaftEndpoint? = getLeaderEndpoint()
        if (leaderEndpoint != null) {
            return raftNodes.first { node -> node.localEndpoint == leaderEndpoint }
        }

        try {
            TimeUnit.MILLISECONDS.sleep(100)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    throw AssertionError("Could not elect a leader on time!")
}

private fun getLeaderEndpoint(): RaftEndpoint? {
    var leaderEndpoint: RaftEndpoint? = null
    var leaderTerm = 0
    for (raftNode in raftNodes) {
        if (raftNode.status == RaftNodeStatus.TERMINATED) {
            continue
        }

        val term = raftNode.term
        if (term.leaderEndpoint != null) {
            if (leaderEndpoint == null) {
                leaderEndpoint = term.leaderEndpoint
                leaderTerm = term.term
            } else if (!(leaderEndpoint == term.leaderEndpoint && leaderTerm == term.term)) {
                leaderEndpoint = null
                break
            }
        } else {
            leaderEndpoint = null
            break
        }
    }

    return leaderEndpoint
}
