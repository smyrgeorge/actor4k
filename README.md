# actor4k

It Is a small and simple actor system using Kotlin and Coroutines (kotlinx.coroutines).

The primary objective is to create a minimal actor system capable of functioning in cluster mode.

## Work in progress

The project is in a very early stage.
Check the `examples` for additional info.

## Todo

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding (in progress)
    - [x] Use `raft` consensus algorithm for the cluster node membership (in progress).
    - [x] Implement `tell/ask` patterns across cluster nodes
    - [x] Add support for cross-node actor reference
    - [x] Introduce the concept of Shard.
    - [ ] Shard management and rebalancing.
- [ ] Serialization (in progress)
    - [x] Send messages across cluster using the gossip protocol
    - [x] Use gRPC for communication across cluster nodes
    - [x] Use protobuf for actor messages (using kotlinx protobuf)
- [ ] Metrics (in progress)
- [ ] Logging (in progress)
- [ ] Java compatibility
- [ ] Persistence

## Run the example.

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