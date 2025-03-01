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
// Base package
implementation("io.github.smyrgeorge:actor4k:x.y.z")

// If your project is targets Kotlin-Multiplatform, simply use:
implementation("io.github.smyrgeorge:actor4k-kmp:x.y.z")

// Or If your project targets JVM, simply use:
implementation("io.github.smyrgeorge:actor4k-jvm:x.y.z")
```



### Start up the Actor System

`actor4k` tries to be multiplatform compatible, which means there is no support for reflection. Therefore, we must pass
the factories to the registry for each Actor class in our project (see the example below).

```kotlin
// Create the Actor Registry.
val registry = SimpleActorRegistry()
    .register(AccountActor::class) { AccountActor(it) }

// Start the actor system.
ActorSystem
    .register(KmpLoggerFactory()) // Part of the actor4k-kmp dependency.
    // .register(JLoggerFactory()) // Additionally you can use the JLoggerFactory (part of the actor4k-jvm dependency.)
    .register(SimpleStats())
    .register(registry)
    .start()
```

### Let's create an Actor!

```kotlin
data class Req(val msg: String)
data class Resp(val msg: String)

data class AccountActor(
    override val key: String
) : Actor(key) {

    override suspend fun onBeforeActivate() {
        log.info("[${address()}] before-activate")
    }

    override suspend fun onActivate(m: Message) {
        log.info("[${address()}] activate ($m)")
    }

    override suspend fun onReceive(m: Message, r: Response.Builder): Response {
        val msg = m.cast<Req>()
        log.info("[${address()}] Received message: $msg")
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

See all the available examples [here](examples).

## Build

```shell
./gradlew build
```

## Links and References

- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
