# actor4k

A small actor system written in kotlin using Coroutines (kotlinx.coroutines).

The main goal is to build a minimal actor system that can work in cluster mode.

For this particular reason we make use of `scalecube-cluster` (implementation of SWIM).

## Todo

A lot of things need to be done, so sit tight…

- [ ] Cluster
- [ ] Persistence
- [ ] Serialization
- [ ] Logging
- [ ] Metrics
- [ ] Java compatibility

## Work in progress
The project is in a very early stage.
Check the `examples` for additional info.

## References
- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
- https://github.com/scalecube/scalecube-cluster/tree/master
- https://www.cs.cornell.edu/projects/Quicksilver/public_pdfs/SWIM.pdf