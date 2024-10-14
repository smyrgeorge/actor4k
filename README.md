# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/actor4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/actor4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/actor4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/actor4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg?logo=kotlin)](http://kotlinlang.org)

A small actor system written in kotlin using Coroutines.

The primary objective is to create a minimal actor system capable of functioning in cluster mode.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/actor4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

### Work in progress

The project is currently under development.
However, you can already use it with the `STATIC` cluster node management without any issues.
We are already planning to use it in production in the very near future.

### Key concepts

- Using the `SWIM` gossip protocol for node/network discovery and low level communication.
- Using `gRPC` for the necessary communications from one node to another.
- Using the `raft` consensus algorithm for (only for `DYNAMIC` node management):
    - `leader election`: the leader is responsible to manage the cluster state (add/remove nodes)
    - `maintain cluster state`: replicate the cluster state across the nodes of the network

### Usage

```xml

<dependency>
    <groupId>io.github.smyrgeorge</groupId>
    <artifactId>actor4k</artifactId>
    <version>x.y.z</version>
</dependency>
```

or using gradle:

```kotlin
implementation("io.github.smyrgeorge:actor4k:x.y.z")
```

### Let's create an Actor!

```kotlin
data class Req(val msg: String)
data class Resp(val msg: String)

data class AccountActor(
    override val shard: String,
    override val key: String
) : Actor(shard, key) {
    override suspend fun onBeforeActivate() {
        log.info { "[${address()}] before-activate" }
    }

    override suspend fun onActivate(m: Message) {
        log.info { "[${address()}] activate ($m)" }
    }
  
    override fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Req>()
        log.info { "[$name] Received message: $msg" }
        val res = Resp("Pong!")
        return r.value(res).build()
    }
}
```

Now let's send some messages:

```kotlin
val a: Actor.Ref = ActorSystem.get(AccountActor::class, "ACC0010")

val req = Req(msg = "[tell] Hello World!")
a.tell(req)

val req2 = Req(msg = "[ask] Ping!")
val r = a.ask<Resp>(req2)
println(r)
```

See other examples [here](examples%2Fsrc%2Fmain%2Fkotlin%2Fio%2Fgithub%2Fsmyrgeorge%2Factor4k%2Fexamples).

You can also check the `microbank` example [here](microbank).
Microbank is small banking account simulator that we have created to test `actor4k`.

### Node management

We offer 2 types of node management

- `STATIC`: The cluster is initialized with a fixed number of nodes, and any changes to the network will not be applied.
  For instance, if a node restarts or stops, the other nodes will continue sending traffic to that node. This mode can
  be a suitable option for small clusters (e.g., 2-5 nodes) as it simplifies the cluster's operation and management.
- `DYNAMIC`: The cluster will be initialized with a set of nodes (`seed-members`). Then, the leader of the network will
  scan for changes and initiate a shard migration process for each change. For example, when a new node is discovered,
  it will be added to the network after the shard migration is completed. Please note that this functionality has not
  been thoroughly tested yet, so we may encounter data corruption.

### Let's start up the cluster

```kotlin
val alias = System.getenv("ACTOR4K_NODE_ID") ?: "bank-1"
val host = System.getenv("ACTOR4K_NODE_HOST") ?: alias
val httpPort = System.getenv("ACTOR4K_NODE_HTTP_PORT")?.toInt() ?: 9000
val grpcPort = System.getenv("ACTOR4K_NODE_GRPC_PORT")?.toInt() ?: 61100
val gossipPort = System.getenv("ACTOR4K_NODE_GOSSIP_PORT")?.toInt() ?: 61000
val seedMembers: List<Cluster.Conf.Node> =
    (System.getenv("ACTOR4K_SEED_MEMBERS") ?: "$alias::localhost:$gossipPort")
        .split(",")
        .map { Cluster.Conf.Node.from(it) }

val conf = Cluster.Conf
    .Builder()
    .alias(alias)
    .host(host)
    .namespace("actor4k")
    .grpcPort(grpcPort)
    .gossipPort(gossipPort)
    .nodeManagement(Cluster.Conf.NodeManagement.STATIC)
    .seedMembers(seedMembers)
    .build()

log.info { conf }

Cluster
    .Builder()
    .conf(conf)
    .build()
    .start()
```

Check the [microbank](microbank) example for more information.

### Working with Java

We provide special utilities to accomplish this.
Whenever you need to, simply call the asJava() method and the magic will happen.

For instance take a look here.

```java
ActorSystem system = ActorSystem.INSTANCE.start(ActorSystem.INSTANCE.getConf());
Actor.Ref ref = system.getRegistry().asJava().get(AccountActor.class, "ACC00011").join();
System.out.println(ref);
```

You can also find other
examples [here](examples%2Fsrc%2Fmain%2Fjava%2Fio%2Fgithub%2Fsmyrgeorge%2Factor4k%2Fexamples%2Fjava).

### Progress of the project

A lot of things need to be done, so sit tight…

- [ ] Cluster/Sharding
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
- [x] Java compatibility
- [ ] Benchmark (in progress)
    - [x] GET a single account (JMeter) (
      see [microbank :: get single account.jmx](microbank-bench%2Fsrc%2Fjmeter%2Fmicrobank%20%3A%3A%20get%20single%20account.jmx)).
      Managed 16.6k req/sec with 3 nodes in a Macbook Pro with M1 Max
    - [ ] Deploy `microbank` to a kubernetes cluster.
- [ ] Testing
- [ ] Native Kotlin support (in progress)
- [ ] Metrics/Stats (in progress)
- [ ] Documentation
- [ ] Persistence

### Run the example.

[![asciicast](https://asciinema.org/a/629943.svg)](https://asciinema.org/a/629943)

The included example tries to simulate a basic bank accounting system.

It defines an `AccountActor` that can handle only two simple commands,
`Req.GetAccount` and `Req.ApplyTx`.

The client `microbank-client` generates traffic.
In the end will check the available balance (should be zero).

So, with this example, we validate the cluster consistency.

Run the following script (it will also build the project).

```shell
./scripts/run.sh
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

#### Cleanup

```shell
./gradelw clean && docker compose down -v
```

### Build

```shell
./gradlew build
```

#### Docker

```shell
docker build . -t microbank -f microbank/src/main/docker/Dockerfile
```

### Links and References

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
