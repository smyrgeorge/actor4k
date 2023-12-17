# actor4k

A small actor system written in kotlin using Coroutines (kotlinx.coroutines).

The main goal is to build a minimal actor system that can work in cluster mode.

For this particular reason we make use of `scalecube-cluster` (implementation of the SWIM protocol).

## Work in progress

The project is in a very early stage.
Check the `examples` for additional info.

## Todo

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding (in progress)
  - [x] Implement `tell/ask` patterns across cluster nodes
  - [ ] Add support for cross-node actor reference (in progress)
- [ ] Serialization (in progress)
  - [x] Send messages across cluster using the gossip protocol
  - [ ] Change the serialization method (do not use java serialization)
- [ ] Metrics (in progress)
- [ ] Logging (in progress)
- [ ] Java compatibility
- [ ] Persistence

## Run

Run the following script (it will also build the project).

```shell
./run.sh
```

The above script will do the following:
- Build the project.
- Build the docker image `actor4k:latest`.
- Will run the `docker compose` (will start four custer nodes).

If everything goes well, you should see something like the following (`mPS` consists messages per second):
```text
...
node-1-1  | 55406 [DefaultDispatcher-worker-2] INFO  io.github.smyrgeorge.actor4k.cluster.Cluster  - Stats(members=4, tG=0, gPs=0, tM=288139, mPS=8041)
node-4-1  | 55616 [DefaultDispatcher-worker-4] INFO  io.github.smyrgeorge.actor4k.cluster.Cluster  - Stats(members=4, tG=0, gPs=0, tM=286887, mPS=7950)
node-3-1  | 55719 [DefaultDispatcher-worker-1] INFO  io.github.smyrgeorge.actor4k.cluster.Cluster  - Stats(members=4, tG=0, gPs=0, tM=280909, mPS=7719)
node-2-1  | 55693 [DefaultDispatcher-worker-4] INFO  io.github.smyrgeorge.actor4k.cluster.Cluster  - Stats(members=4, tG=0, gPs=0, tM=279226, mPS=7652)
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

## References

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
- https://github.com/ishugaliy/allgood-consistent-hash