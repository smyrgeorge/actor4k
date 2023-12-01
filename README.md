# actor4k

A small actor system written in kotlin using Coroutines (kotlinx.coroutines).

The main goal is to build a minimal actor system that can work in cluster mode.

For this particular reason we make use of `scalecube-cluster` (implementation of SWIM).

## Todo

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding (in progress)
- [ ] Metrics (in progress)
- [ ] Logging (in progress)
- [ ] Serialization (in progress)
- [ ] Java compatibility
- [ ] Persistence

## Work in progress

The project is in a very early stage.
Check the `examples` for additional info.

## Run

Run the following script (it will also build the project).

```shell
./run.sh
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
- https://github.com/scalecube/scalecube-cluster/tree/master
- https://www.cs.cornell.edu/projects/Quicksilver/public_pdfs/SWIM.pdf
- https://www.baeldung.com/java-dockerize-app
- http://highscalability.com/blog/2023/2/22/consistent-hashing-algorithm.html#:~:text=the%20hash%20ring.-,The%20hash%20ring%20is%20traversed%20in%20the%20clockwise%20direction%20starting,must%20hold%20the%20data%20object.
- https://www.pubnub.com/blog/consistent-hashing-implementation/
- https://github.com/Jaskey/ConsistentHash
- https://github.com/ishugaliy/allgood-consistent-hash
- https://github.com/ishugaliy/allgood-consistent-hash