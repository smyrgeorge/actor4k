package io.github.smyrgeorge.actor4k.examples

import io.github.smyrgeorge.actor4k.examples.AccountBehaviourActor.Protocol
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.SimpleActorRegistry
import io.github.smyrgeorge.actor4k.util.SimpleLoggerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    // Set up a logger factory
    val loggerFactory = SimpleLoggerFactory()

    // Create and register the actor registry
    val registry = SimpleActorRegistry(loggerFactory)
        .factoryFor(AccountBehaviourActor::class) {
            AccountBehaviourActor(key = it)
        }

    // Start the actor system
    ActorSystem
        .register(loggerFactory)
        .register(registry)
        .start()

    // Allow the system to initialize
    delay(1000)
    
    println("Testing AccountBehaviourActor:")
    
    // Get a reference to the actor
    val accountActor = ActorSystem.get(AccountBehaviourActor::class, "account-1")
    
    // Test normal behavior
    println("\n--- Testing Normal Behavior ---")
    val response1 = accountActor.ask(Protocol.Ping("Hello")).getOrThrow()
    println("Response: $response1")
    
    // Switch to echo behavior
    println("\n--- Switching to Echo Behavior ---")
    val switchResponse1 = accountActor.ask(Protocol.SwitchBehavior("echo")).getOrThrow()
    println("Response: $switchResponse1")
    
    // Test echo behavior
    println("\n--- Testing Echo Behavior ---")
    val response2 = accountActor.ask(Protocol.Ping("Hello")).getOrThrow()
    println("Response: $response2")
    
    // Switch back to normal behavior
    println("\n--- Switching back to Normal Behavior ---")
    val switchResponse2 = accountActor.ask(Protocol.SwitchBehavior("normal")).getOrThrow()
    println("Response: $switchResponse2")
    
    // Test normal behavior again
    println("\n--- Testing Normal Behavior Again ---")
    val response3 = accountActor.ask(Protocol.Ping("Hello")).getOrThrow()
    println("Response: $response3")
    
    // Allow time for logs to be printed
    delay(1000)
}