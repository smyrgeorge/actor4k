# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)

It Is a small and simple actor system using Kotlin and Coroutines (kotlinx.coroutines).

The primary objective is to create a minimal actor system capable of functioning in cluster mode.

# Key concepts

- Using the `SWIM` gossip protocol for node/network discovery and low level communication.
- Using the `raft` consensus algorithm for:
    - `leader election`: the leader is responsible to manage the cluster state (add/remove nodes)
    - `cluster state` across the nodes of the network
- Using `gRPC` for the necessary communications from one node to another.

## Work in progress

The project is in a very early stage.
Check the `examples` for additional info.

## Progress of the project

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding (in progress)
    - [x] Use `raft` consensus algorithm for the cluster node membership (control the state of the cluster).
    - [x] Implement `tell/ask` patterns across cluster nodes
    - [x] Add support for cross-node actor reference
    - [x] Introduce the concept of Shard
    - [ ] Shard management and rebalancing (in progress)
    - [ ] Graceful shutdown
    - [ ] Error handling
- [ ] Serialization (in progress)
    - [x] Send protocol messages using the gossip protocol
    - [x] Use gRPC for sending messages from an actor to another actor (in the case of different nodes)
    - [x] Use protobuf for actor messages (kotlinx protobuf)
- [ ] Logging (in progress)
    - [ ] Configure log4j/slf4j
    - [ ] Disable unnecessary messages
- [ ] Benchmark (in progress)
    - [ ] Load test using Jmeter (or another tool)
- [ ] Testing
- [ ] Metrics (in progress)
- [ ] Documentation
- [ ] Java compatibility
- [ ] Persistence

## Run the example.

[![asciicast](https://asciinema.org/a/629943.svg)](https://asciinema.org/a/629943)

The included example tries to simulate a basic bank accounting system.

It defines an `AccountActor` that can handle only two simple commands,
`Req.GetAccount` and `Req.ApplyTx`.

The client `examples-bank-client` generates traffic.
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

Then you can run the `bank-client` in order to generate some traffic:

```shell
java -jar examples-bank-client/build/libs/examples-bank-client-0.1.0.jar
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
docker build . -t actor4k -f examples/src/main/docker/Dockerfile
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