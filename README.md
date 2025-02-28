# actor4k

![Build](https://github.com/smyrgeorge/actor4k/actions/workflows/ci.yml/badge.svg)
![Maven Central](https://img.shields.io/maven-central/v/io.github.smyrgeorge/actor4k)
![GitHub License](https://img.shields.io/github/license/smyrgeorge/actor4k)
![GitHub commit activity](https://img.shields.io/github/commit-activity/w/smyrgeorge/actor4k)
![GitHub issues](https://img.shields.io/github/issues/smyrgeorge/actor4k)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin)](http://kotlinlang.org)

A small actor system written in kotlin using Coroutines.

> [!IMPORTANT]  
> The project is in a very early stage; thus, breaking changes should be expected.

📖 [Documentation](https://smyrgeorge.github.io/actor4k/)

🏠 [Homepage](https://smyrgeorge.github.io/) (under construction)

### Usage

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

### Working with Java

We provide special utilities to accomplish this.
Whenever you need to, simply call the asJava() method and the magic will happen.

For instance take a look here.

```java
ActorSystem system = ActorSystem.INSTANCE.start(ActorSystem.INSTANCE.getConf());
Actor.Ref ref = system.getRegistry().asJava().get(AccountActor.class, "ACC00011").join();
System.out.println(ref);
```

### Build

```shell
./gradlew build
```

### Links and References

- https://kotlinlang.org/docs/coroutines-guide.html
- https://doc.akka.io/docs/akka/current/general/actor-systems.html
- https://en.wikipedia.org/wiki/Actor_model
- https://www.baeldung.com/kotlin/suspend-functions-from-java
