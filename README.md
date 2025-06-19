# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/actor4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/actor4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/actor4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/actor4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.21-blue.svg?logo=kotlin)](http://kotlinlang.org)

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

## Actor Model

The actor model is a design paradigm for building concurrent systems where the basic unit of computation, known as an
actor, encapsulates its own state and behavior and interacts with others solely through asynchronous message passing.
Each actor processes messages sequentially, which simplifies managing state changes and avoids common pitfalls like race
conditions and deadlocks that arise with traditional multithreading approaches.

This model is particularly useful for highly concurrent, distributed, and fault-tolerant systems. Its scalability and
resilience come from the ability to isolate errors within individual actors through supervision strategies, making it a
fitting choice for applications such as real-time data processing, microservices architectures, and any system that
requires robust fault isolation and maintainability.

## Supported Features

### Core Features

- **Actor Lifecycle Management**: Creation, activation, and graceful shutdown of actors
- **Message Passing Patterns**:
    - `tell` (fire-and-forget) for asynchronous communication
    - `ask` (request-response) for synchronous communication with timeout support
- **Actor References**: Type-safe references to actors for communication
- **Back-Pressure Control**: Configurable mailbox capacity to handle high message volumes
- **Error Isolation**: Errors in one actor don't affect others
- **Stashing**: Ability to temporarily store messages for later processing
- **Statistics Tracking**: Monitor actor performance and message processing

### Actor Types

- **Generic Actor**: Base actor implementation with lifecycle hooks
- **Behavior Actor**: Actors that can change their behavior dynamically at runtime
- **Router Actor**: Distributes messages to worker actors using various routing strategies
    - Random routing
    - Round-robin routing
    - Broadcast routing
    - First-available routing

### Cluster Support

- **Distributed Actor Communication**: Seamless communication between actors across multiple nodes
- **Remote Actor References**: References to actors on remote nodes
- **Node Management**: Configuration and coordination of cluster nodes
- **RPC Communication**: Remote procedure calls between nodes using WebSockets
- **Message Serialization**: Efficient serialization of messages for network transport

### Multiplatform Support

- **JVM**: Support for Java Virtual Machine
- **Native**: Support for Linux, macOS, Windows
- **Mobile**: Support for iOS and Android
- **WebAssembly**: Support for wasmJs and wasmWasi

### State of the project

**Note:** This project is still under heavy development, so you might encounter some incompatibilities along the way.
I'm currently focusing on enhancing the `cluster` module (you can check it out [here](actor4k-cluster)). That said, the
core module is already being used in production with great results (in a non-cluster environment).

**What’s Next?**

- **Enhance the basic actor API by adding:**
    - Timers
- **Expand the cluster capabilities**
    - Node configuration loader
    - Dynamic node management and discovery
- **Other**
    - Event Sourcing tooling (Persistent Actor)

## Actors in Cluster

Cluster support in the **actor4k** provides essential capabilities for building robust, scalable, and highly available
systems. By enabling actors to seamlessly communicate and coordinate across multiple nodes, clustering significantly
enhances fault tolerance, ensures workload distribution efficiency, and maintains stable performance in distributed and
high-concurrency environments. This functionality is currently under active development; you can follow its progress and
contribute to its advancement through the [actor4k-cluster](actor4k-cluster) module.

## Usage

```kotlin
implementation("io.github.smyrgeorge:actor4k:x.y.z")
```

### Start up the Actor System

**actor4k** tries to be multiplatform compatible, which means there is no support for reflection.
Therefore, we must pass the factories to the registry for each Actor class in our project (see the example below).

```kotlin
// Create the Logger Factory.
val loggerFactory = SimpleLoggerFactory()
// Create the Actor Registry.
val registry = SimpleActorRegistry(loggerFactory)
    .factoryFor(AccountActor::class) { key ->
        AccountActor(key) // You can define how an Actor is created.
        // You can also pass other arguments to the Actor at this point like, for example,
        // AccountActor(key, arg1, ...)
        // This can be very helpful with dependency injection scenarios.
    }

// Start the actor system.
ActorSystem
    .register(loggerFactory)
    .register(registry) // You can override the registry implementation here.
    .start()
```

### Let's define an Actor!

```kotlin
class AccountActor(key: String) : Actor<Protocol, Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        // Optional override.
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        // Optional override.
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        log.info("[${address()}] onReceive: $m")
        return when (m) {
            is Protocol.Ping -> Protocol.Pong("Pong!")
        }
    }

    override suspend fun onShutdown() {
        // Optional override.
        log.info("[${address()}] onShutdown")
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Ping(val message: String) : Message<Pong>()
        data class Pong(val message: String) : Response()
    }
}
```

This example shows a basic actor implementation. If you need an actor that can change its behavior dynamically at
runtime (for implementing state machines or context-dependent processing), check out
the [Behavior Actor](#behavior-actor) section below.

Now let's send some messages:

```kotlin
// [Create/Get] the desired actor from the registry.
val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
// [Tell] something to the actor (asynchronous operation).
actor.tell(Protocol.Req(message = "[tell] Hello World!")).getOrThrow()
// [Ask] something to the actor (synchronous operation).
val res = actor.ask(Protocol.Req(message = "[ask] Ping!")).getOrThrow()
println(res)
```

See all the available examples [here](examples/src).

### Actor registry

The actor registry is a central component within an actor system that is responsible for managing the lifecycle of actor
instances. It maintains a mapping between unique actor addresses and their corresponding instances, ensuring that each
actor can be efficiently retrieved and managed. Additionally, the registry stores factory functions for various actor
types, which are used to dynamically create new actors when requested. By handling registration, retrieval, and cleanup
of actors in a thread-safe manner, the actor registry plays a crucial role in supporting the scalability and reliability
of the overall system.

```kotlin
// [Create/Get] the desired actor from the registry.
val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
```

### Detached actors

A detached actor is an actor instance manually created by directly invoking its constructor, rather than being
automatically instantiated and managed by the actor registry. This means that it stands apart from the typical lifecycle
management and messaging infrastructure of the actor system until it is explicitly integrated. Detached actors are
useful in scenarios where you need fine-grained control over the actor's initialization or when they play a specialized
role that doesn't require the full orchestration provided by the system's registry mechanisms.

```kotlin
// [Create] the desired actor.
// We also need to manually [activate] the actor.
val detached = AccountActor("DETACHED").apply { activate() }
detached.tell(Protocol.Req(message = "[ask] Ping!"))
// This actor will never close until we call the shutdown method.
detached.shutdown()
```

## Actor types

### Generic Actor

The generic actor serves as an abstract foundation for creating actors tailored specifically to your application's
requirements. Defined through the [Actor](actor4k/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/actor/Actor.kt)
abstract class, it provides a structured way to implement essential behaviors needed in concurrent environments.
By extending the `Actor` base class, you gain access to built-in lifecycle hooks such as:

- **`onBeforeActivate`**: opens a hook prior to activation, ideal for initial configuration or asynchronous
  preparations.
- **`onActivate`**: a method triggered upon receiving the first initialization message, enabling state initialization
  or custom setup logic.
- **`onReceive`**: a central message handler, mandatory for defining an actor’s response logic.
- **`onShutdown`**: a finalization hook useful for closing resources, saving state, or performing cleanup procedures
  while gracefully shutting down.

Each actor instance encapsulates its unique state and interacts exclusively through asynchronous message passing,
ensuring thread-safe operation and simplifying concurrency management.

#### Message Stashing

The [Actor](actor4k/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/actor/Actor.kt) class provides a message stashing
mechanism that allows actors to temporarily defer processing of certain messages. This is particularly useful when an
actor receives messages that it cannot or should not process in its current state, but wants to handle later.

Key features of the stashing mechanism:

- **`stash(msg)`**: Temporarily stores a message in a separate queue for later processing
- **`unstashAll()`**: Moves all stashed messages back to the actor's mailbox, preserving their original order
- The actor keeps track of stashed messages count in its statistics

This mechanism is especially valuable when implementing state-dependent behavior, where messages might arrive in an
order different from what the actor can process, or when the actor needs to change its behavior before processing
certain messages.

#### Back-Pressure

The [Actor](actor4k/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/actor/Actor.kt) class includes a `capacity`
property, which determines the maximum number of messages allowed in an actor's mailbox. By default, this is set to
unlimited. Adjusting this property enables you to control mailbox capacity explicitly. If the mailbox reaches its
maximum capacity, any following attempts to send messages will suspend until space becomes available. Leveraging this
behavior provides an effective way to implement a back-pressure strategy within your actor-based application.

### Router Actor

A [Router Actor](actor4k/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/actor/impl/RouterActor.kt) is an actor
designed to distribute received messages among multiple worker actors according to a defined routing strategy. This
pattern simplifies concurrent and parallel message handling, effectively supporting greater scalability and throughput.
The RouterActor provides mechanisms for dynamic management and structured communication patterns with its worker
actors, encapsulating common strategies useful in concurrent systems.

#### Key Features and Responsibilities

The `RouterActor` extends the foundational `Actor` abstraction, managing several essential roles:

- **Message Routing**: It efficiently forwards messages it receives to its registered worker actors based on a selected
  routing strategy.
- **Worker Actor Management**:
    - Initiates and manages the lifecycle of worker actors, ensuring they are appropriately activated, maintained, and
      gracefully shut down.

#### Supported Routing Strategies:

The Router Actor provides four routing strategies:

- **RANDOM**: Chooses a random worker to deliver the message.
- **BROADCAST**: Delivers the message to all workers. Note that the `ask` operation cannot be used with this strategy.
- **ROUND_ROBIN**: Sends the message to workers in a circular order, balancing the load evenly.
- **FIRST_AVAILABLE**: Routes the message to the first worker that becomes available. This strategy requires
  workers to register their availability with the RouterActor.

#### Worker and Protocol Classes

The RouterActor implementation includes:

- **Worker**: An abstract class that extends Actor and processes messages of specific request and response types.
  Workers have an internal mechanism to signal their availability, which is particularly important for the
  FIRST_AVAILABLE routing strategy.
- **Protocol**: An abstract class that defines the communication structure for messages and responses within the
  actor-based messaging system.

Check an example [here](examples/src/jvmMain/kotlin/io/github/smyrgeorge/actor4k/examples/RouterActorMain.kt).

### Behavior Actor

A [Behavior Actor](actor4k/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/actor/impl/BehaviorActor.kt) is an actor
that can change its behavior dynamically based on the current state or message received. This pattern enables an actor
to respond differently to the same message types depending on its current state, making it ideal for implementing state
machines or actors that need to adapt their processing logic based on previous interactions.

#### Key Features and Capabilities

The `BehaviorActor` extends the foundational `Actor` abstraction, providing several powerful capabilities:

- **Dynamic Behavior Changes**: It allows for changing the actor's message processing logic at runtime using the
  `become` method, enabling state-dependent responses.
- **Behavior Encapsulation**: Behaviors are encapsulated as functions that process messages, making it easy to define
  and switch between different processing strategies.
- **State Machine Implementation**: The ability to change behaviors makes it straightforward to implement state machines
  where the actor's response depends on its current state.

#### Using BehaviorActor

To use a BehaviorActor, you need to:

1. **Define Behaviors**: Create functions that process messages and return appropriate responses.
2. **Switch Behaviors**: Use the `become` method to change the actor's current behavior based on conditions or messages.
3. **Initialize**: Set an initial behavior that the actor will use when it starts processing messages.

#### Utility Methods

The BehaviorActor provides utility methods to simplify behavior management:

- **`become`**: Changes the actor's current behavior to a new function.

#### Example Implementation

The BehaviorActor can be used to implement actors that need to change their behavior based on their state or the
messages they receive. For example, an account actor might switch between normal and echo behaviors:

```kotlin
class AccountBehaviourActor(key: String) : BehaviorActor<Protocol, Protocol.Response>(key) {

    override suspend fun onActivate(m: Protocol) {
        // Set the default behavior here.
        become(normalBehavior)
    }

    sealed interface Protocol : ActorProtocol {
        sealed class Message<R : ActorProtocol.Response> : Protocol, ActorProtocol.Message<R>()
        sealed class Response : ActorProtocol.Response()

        data class Ping(val message: String) : Message<Pong>()
        data class Pong(val message: String) : Response()
        data class SwitchBehavior(val behavior: String) : Message<BehaviorSwitched>()
        data class BehaviorSwitched(val message: String) : Response()
    }

    companion object {
        private val normalBehavior: suspend (AccountBehaviourActor, Protocol) -> Protocol.Response = { ctx, m ->
            ctx.log.info("[${ctx.address()}] normalBehavior: $m")

            when (m) {
                is Protocol.Ping -> Protocol.Pong("Pong!")
                is Protocol.SwitchBehavior -> {
                    ctx.become(echoBehavior)
                    Protocol.BehaviorSwitched("Switched to echo behavior")
                }
            }
        }

        private val echoBehavior: suspend (AccountBehaviourActor, Protocol) -> Protocol.Response = { ctx, m ->
            ctx.log.info("[${ctx.address()}] echoBehavior: $m")

            when (m) {
                is Protocol.Ping -> Protocol.Pong("Echo: ${m.message}")
                is Protocol.SwitchBehavior -> {
                    ctx.become(normalBehavior)
                    Protocol.BehaviorSwitched("Switched to normal behavior")
                }
            }
        }
    }
}
```

Check a complete
example [here](examples/src/commonMain/kotlin/io/github/smyrgeorge/actor4k/examples/AccountBehaviourActor.kt).

## Build

```shell
./gradlew build
```

You can also build for specific targets.

```shell
./gradlew build -Ptargets=macosArm64,macosX64
```

To build for all available targets, run:

```shell
./gradlew build -Ptargets=all
```

## Links and References

- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
