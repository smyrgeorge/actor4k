# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/actor4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/actor4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/actor4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/actor4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

![](https://img.shields.io/static/v1?label=&message=Platforms&color=grey)
![](https://img.shields.io/static/v1?label=&message=Jvm&color=blue)
![](https://img.shields.io/static/v1?label=&message=Linux&color=blue)
![](https://img.shields.io/static/v1?label=&message=macOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Windows&color=blue)
![](https://img.shields.io/static/v1?label=&message=iOS&color=blue)
![](https://img.shields.io/static/v1?label=&message=Android&color=blue)
![](https://img.shields.io/static/v1?label=&message=wasmJs&color=blue)
![](https://img.shields.io/static/v1?label=&message=wasmWasi&color=blue)

A small actor system written in kotlin using Coroutines.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/actor4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

## Cluster Support

The actor4k library extends the actor model with robust clustering capabilities, allowing actors to communicate
seamlessly across multiple nodes in a distributed system. This clustering functionality enables building resilient,
scalable applications that can span multiple servers or containers.

## Node Management

In this early version of the project we only support clusters with `static` size.

The cluster is initialized with a fixed number of nodes, and any changes to the network will not be applied. For
instance, if a node restarts or stops, the other nodes will continue sending traffic to that node. This mode can be a
suitable option for small clusters (e.g., 2-5 nodes) as it simplifies the cluster's operation and management.

### Key Cluster Features

- **Distributed Actor Registry**: Through `ClusterActorRegistry`, actors can be registered once and referenced from any
  node in the cluster
- **Transparent Communication**: Send messages to remote actors using the same API as local actors with `tell` and `ask`
  operations
- **Node Management**: Define and configure multiple cluster nodes with unique identifiers and network locations
- **Serialization**: Built-in support for message serialization using Kotlin Serialization, with customizable
  serialization modules
- **RPC Communication**: Underlying HTTP/WebSocket-based communication layer for reliable message passing between nodes

### Setting Up a Cluster

Creating a cluster with actor4k is straightforward:

``` kotlin
// The following configuration is for a cluster with only one node.
// See the 'microbank' project for a more advanced configuration.
// Define the current node and all nodes in the cluster
val current = ClusterNode.of("node1::localhost:6000")
val nodes = listOf(current)

// Create a logger factory
val loggerFactory = SimpleLoggerFactory()

// Set up the actor registry with your actor classes
val registry = ClusterActorRegistry()
    .factoryFor(AccountActor::class) { AccountActor(it) }

// Initialize the cluster implementation
val cluster = ClusterImpl(
    nodes = nodes,
    current = current,
    registry = registry,
    loggerFactory = loggerFactory,
    routing = {
        // Optional: Add custom HTTP routes
        // Add extra routing to the underlying HTTP server.
    },
    // Configure serialization for your messages.
    serialization = {
        polymorphic(Message::class) {
            subclass(YourMessageType::class, YourMessageType.serializer())
        }
        polymorphic(Message.Response::class) {
            subclass(YourResponseType::class, YourResponseType.serializer())
        }
    },
)

// Start the actor system with cluster support
ActorSystem
    .register(loggerFactory)
    .register(registry)
    .register(cluster)
    .start()
```

For more information feel free to check:

- the project's [examples](../examples/src/jvmMain/kotlin/io/github/smyrgeorge/actor4k/examples/ClusterMain.kt)
- also take a look at [microbank](../microbank) project.

### Working with Remote Actors

Once your cluster is set up, you can work with remote actors just like local ones:

``` kotlin
// Get a reference to an actor (local or remote)
val actorRef = registry.get(YourActorClass::class, "unique-actor-id")

// Send a fire-and-forget message
actorRef.tell(YourMessage("Hello from another node!"))

// Send a message and wait for a response
val response = actorRef.ask<ResponseType>(YourQuestion("What's the status?"))
```

### Fault Tolerance

The cluster implementation handles node failures gracefully:

- Automatic reconnection attempts when a node becomes unavailable.
- Message delivery guarantees with retry mechanisms.

### Implementation Details

Under the hood, the cluster implementation uses:

- Ktor for HTTP/WebSocket communication between nodes.
- Protocol Buffers for efficient message serialization.
- Coroutine-based asynchronous processing for non-blocking operations.

## Getting Started

To include actor4k with cluster support in your project:

``` kotlin
dependencies {
    implementation("io.github.smyrgeorge:actor4k-cluster:x.y.z")
}
```

Check out the [documentation](https://smyrgeorge.github.io/actor4k/) for detailed examples and API references.