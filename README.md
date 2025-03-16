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

## Usage

```kotlin
implementation("io.github.smyrgeorge:actor4k:x.y.z")
```

### State of the project

**Note:** This project is still under heavy development, so you might encounter some incompatibilities along the way.
I'm currently focusing on enhancing the `cluster` module (you can check it out [here](actor4k-cluster)). That said, the
core module is already being used in production with great results (in a non-cluster environment).

**What’s Next?**

- **Enhance the basic actor API by adding:**
  - Timers
  - Stash
  - Router
- **Expand the cluster capabilities**
  - Node configuration loader
  - Dynamic node management and discovery

### Start up the Actor System

`actor4k` tries to be multiplatform compatible, which means there is no support for reflection. Therefore, we must pass
the factories to the registry for each Actor class in our project (see the example below).

```kotlin
// Create the Actor Registry.
val registry = SimpleActorRegistry()
    .factoryFor(AccountActor::class) { key ->
        AccountActor(key) // You can define how an Actor is created.
        // You can also pass other arguments to the Actor at this point like for example:
        // AccountActor(key, arg1, ...)
        // This can be very helpful with dependency injection scenarios.
    }

// Start the actor system.
ActorSystem
    .register(SimpleLoggerFactory())
    .register(registry) // You can override the registry implementation here.
    .start()
```

### Let's define an Actor!

```kotlin
class AccountActor(
    override val key: String
) : Actor<Protocol, Protocol.Response>(key) {
    override suspend fun onBeforeActivate() {
        log.info("[${address()}] onBeforeActivate")
    }

    override suspend fun onActivate(m: Protocol) {
        log.info("[${address()}] onActivate: $m")
    }

    override suspend fun onReceive(m: Protocol): Protocol.Response {
        log.info("[${address()}] onReceive: $m")
        val res = when (m) {
            is Protocol.Req -> Protocol.Req.Resp("Pong!")
        }
        return res
    }

    sealed class Protocol : Message() {
        sealed class Response : Message.Response()
        data class Req(val message: String) : Protocol() {
            data class Resp(val message: String) : Response()
        }
    }
}
```

Now let's send some messages:

```kotlin
import io.github.smyrgeorge.actor4k.examples.AccountActor.Protocol

// [Create/Get] the desired actor from the registry.
val actor: ActorRef = ActorSystem.get(AccountActor::class, "ACC0010")
// [Tell] something to the actor (asynchronous operation).
actor.tell(Protocol.Req(message = "[tell] Hello World!"))
// [Ask] something to the actor (synchronous operation).
val res = actor.ask<Protocol.Req.Resp>(Protocol.Req(message = "[ask] Ping!")).getOrThrow()
println(res)
```

See all the available examples [here](examples).

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

A detached actor is an actor instance that is manually created by directly invoking its constructor, rather than being
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

## Build

```shell
./gradlew build
```

You can also build for specific targets.

```shell
./gradlew build -Ptargets=macosArm64,macosX64
```

To build for all available target run:

```shell
./gradlew build -Ptargets=all
```

## Links and References

- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
