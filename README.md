# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)

It Is a small and simple actor system using Kotlin and Coroutines (kotlinx.coroutines).

The primary objective is to create a minimal actor system capable of functioning in cluster mode.

## Key concepts

- Using the `SWIM` gossip protocol for node/network discovery and low level communication.
- Using `gRPC` for the necessary communications from one node to another.
- Using the `raft` consensus algorithm for (only for `DYNAMIC` node management):
  - `leader election`: the leader is responsible to manage the cluster state (add/remove nodes)
  - `maintain cluster state`: replicate the cluster state across the nodes of the network

## Node management
We offer 2 types of node management
- `STATIC`: The cluster is initialized with a fixed number of nodes, and any changes to the network will not be applied. For instance, if a node restarts or stops, the other nodes will continue sending traffic to that node. This mode can be a suitable option for small clusters (e.g., 2-5 nodes) as it simplifies the cluster's operation and management. 
- `DYNAMIC`: The cluster will be initialized with a set of nodes (`seed-members`). Then, the leader of the network will scan for changes and initiate a shard migration process for each change. For example, when a new node is discovered, it will be added to the network after the shard migration is completed. Please note that this functionality has not been thoroughly tested yet, so we may encounter data corruption.

## Work in progress

The project is in a very early stage.
Check the `examples` for additional info.

## Progress of the project

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding (in progress)- 
    - [x] Support `STATIC/DYNAMIC` node management.
    - [x] Use `raft` consensus algorithm for the cluster node membership (control the state of the cluster).
    - [x] Implement `tell/ask` patterns across cluster nodes
    - [x] Add support for cross-node actor reference
    - [x] Introduce the concept of Shard
    - [x] Shard management and re-balance shards after a node joins/leaves cluster
    - [ ] Error handling
    - [ ] Review/Optimize `MemberManager`
    - [ ] Graceful shutdown
- [x] Serialization
    - [x] Send protocol messages using the gossip protocol
    - [x] Use gRPC for sending messages from an actor to another (in the case of different nodes)
    - [x] Use protobuf for actor messages (kotlinx protobuf)
- [ ] Logging (in progress)
    - [ ] Configure log4j/slf4j
    - [ ] Disable unnecessary messages
- [ ] Benchmark (in progress)
    - [x] GET a single account (JMeter) (see [microbank :: get single account.jmx](microbank-bench%2Fsrc%2Fjmeter%2Fmicrobank%20%3A%3A%20get%20single%20account.jmx)). Managed 16.6k req/sec with 3 nodes in a Macbook Pro with M1 Max
    - [ ] Deploy `microbank` to a kubernetes cluster.
    - [ ] Load test with gatling (in progress)
- [ ] Testing
- [ ] Metrics/Stats (in progress)
- [ ] Documentation
- [ ] Java compatibility
- [ ] Persistence

## Run the example.

[![asciicast](https://asciinema.org/a/629943.svg)](https://asciinema.org/a/629943)

The included example tries to simulate a basic bank accounting system.

It defines an `AccountActor` that can handle only two simple commands,
`Req.GetAccount` and `Req.ApplyTx`.

The client `microbank-client` generates traffic.
In the end will check the available balance (should be zero).

So, with this example, we validate the cluster consistency.

Run the following script (it will also build the project).

```shell
./run.sh
```

The above script will do the following:

- Build the project.
- Build the docker image `actor4k-bank:latest`.
- Run the `docker compose` that will start:
    - 3 custer bank nodes
    - a nginx acting as the loadbalancer

Then you can run the `microbank-client` in order to generate some traffic:

```shell
java -jar microbank-client/build/libs/microbank-client-0.1.0.jar
```

### Cleanup

```shell
./gradelw clean && docker compose down -v
```

## Build

```shell
./gradlew build
```

### Docker

```shell
docker build . -t microbank -f microbank/src/main/docker/Dockerfile
```

## Links and References

- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
- https://github.com/scalecube/scalecube-cluster
- https://www.cs.cornell.edu/projects/Quicksilver/public_pdfs/SWIM.pdf
- https://www.baeldung.com/java-dockerize-app
- http://highscalability.com/blog/2023/2/22/consistent-hashing-algorithm.html#:~:text=the%20hash%20ring.-,The%20hash%20ring%20is%20traversed%20in%20the%20clockwise%20direction%20starting,must%20hold%20the%20data%20object.
- https://www.pubnub.com/blog/consistent-hashing-implementation/
- https://github.com/Jaskey/ConsistentHash
- https://github.com/ishugaliy/allgood-consistent-hash
- https://microraft.io
- https://raft.github.io