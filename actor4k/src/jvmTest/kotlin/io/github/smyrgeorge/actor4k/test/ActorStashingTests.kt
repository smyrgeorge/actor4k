package io.github.smyrgeorge.actor4k.test

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isZero
import io.github.smyrgeorge.actor4k.actor.ref.ActorRef
import io.github.smyrgeorge.actor4k.actor.ref.LocalRef
import io.github.smyrgeorge.actor4k.system.ActorSystem
import io.github.smyrgeorge.actor4k.system.registry.ActorRegistry
import io.github.smyrgeorge.actor4k.test.actor.StashingActor
import io.github.smyrgeorge.actor4k.test.actor.StashingActor.Protocol
import io.github.smyrgeorge.actor4k.test.util.Registry
import io.github.smyrgeorge.actor4k.util.extentions.AnyActor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ActorStashingTests {
    private val registry: ActorRegistry = Registry.registry

    init {
        runBlocking {
            ActorSystem.shutdown()
            ActorSystem.start()
        }
    }

    @Test
    fun `Actor should stash messages in stashing mode`(): Unit = runBlocking {
        // Create a stashing actor
        val ref: ActorRef = ActorSystem.get(StashingActor::class, STASH_ACTOR)
        val actor: AnyActor = registry.getLocalActor(ref as LocalRef)
        
        // Verify initial state
        val initialMode = ref.ask(Protocol.Req("get_mode")).getOrThrow() as Protocol.Resp
        assertThat(initialMode.message).isEqualTo("Current mode: STASHING")
        
        // Send messages that should be stashed
        ref.tell(Protocol.Req("message1"))
        ref.tell(Protocol.Req("message2"))
        ref.tell(Protocol.Req("message3"))
        
        // Verify that no messages have been processed yet
        delay(100) // Give time for messages to be received
        val processedCount = ref.ask(Protocol.Req("get_processed_count")).getOrThrow() as Protocol.Resp
        assertThat(processedCount.message).isEqualTo("Processed count: 0")
        
        // Verify that messages have been stashed
        assertThat(actor.stats().stashedMessages).isEqualTo(3)
        
        // Switch to processing mode which should unstash all messages
        ref.ask(Protocol.Req("switch_mode")).getOrThrow()
        
        // Give time for unstashed messages to be processed
        delay(200)
        
        // Verify that all messages have been processed
        val finalProcessedCount = ref.ask(Protocol.Req("get_processed_count")).getOrThrow() as Protocol.Resp
        assertThat(finalProcessedCount.message).isEqualTo("Processed count: 3")
        
        // Verify that stashed messages count is now zero
        assertThat(actor.stats().stashedMessages).isZero()
        
        // Verify the order of processed messages
        val processedMessages = ref.ask(Protocol.Req("get_processed_messages")).getOrThrow() as Protocol.ProcessedMessages
        assertThat(processedMessages.messages).isEqualTo(listOf("message1", "message2", "message3"))
    }
    
    @Test
    fun `Actor should process messages immediately in processing mode`(): Unit = runBlocking {
        // Create a stashing actor
        val ref: ActorRef = ActorSystem.get(StashingActor::class, STASH_ACTOR2)
        
        // Switch to processing mode
        ref.ask(Protocol.Req("switch_mode")).getOrThrow()
        
        // Send messages that should be processed immediately
        ref.tell(Protocol.Req("message1"))
        ref.tell(Protocol.Req("message2"))
        
        // Give time for messages to be processed
        delay(100)
        
        // Verify that messages have been processed
        val processedCount = ref.ask(Protocol.Req("get_processed_count")).getOrThrow() as Protocol.Resp
        assertThat(processedCount.message).isEqualTo("Processed count: 2")
        
        // Verify the order of processed messages
        val processedMessages = ref.ask(Protocol.Req("get_processed_messages")).getOrThrow() as Protocol.ProcessedMessages
        assertThat(processedMessages.messages).isEqualTo(listOf("message1", "message2"))
    }
    
    @Test
    fun `Actor should handle mixed stashing and processing`(): Unit = runBlocking {
        // Create a stashing actor
        val ref: ActorRef = ActorSystem.get(StashingActor::class, STASH_ACTOR3)
        
        // Send messages that should be stashed
        ref.tell(Protocol.Req("stashed1"))
        ref.tell(Protocol.Req("stashed2"))
        
        // Switch to processing mode which should unstash all messages
        ref.ask(Protocol.Req("switch_mode")).getOrThrow()
        
        // Send more messages that should be processed immediately
        ref.tell(Protocol.Req("immediate1"))
        ref.tell(Protocol.Req("immediate2"))
        
        // Give time for all messages to be processed
        delay(200)
        
        // Verify that all messages have been processed
        val processedCount = ref.ask(Protocol.Req("get_processed_count")).getOrThrow() as Protocol.Resp
        assertThat(processedCount.message).isEqualTo("Processed count: 4")
        
        // Verify the order of processed messages (stashed messages should be processed first)
        val processedMessages = ref.ask(Protocol.Req("get_processed_messages")).getOrThrow() as Protocol.ProcessedMessages
        assertThat(processedMessages.messages).isEqualTo(
            listOf("stashed1", "stashed2", "immediate1", "immediate2")
        )
    }

    companion object {
        private const val STASH_ACTOR: String = "STASH_ACTOR"
        private const val STASH_ACTOR2: String = "STASH_ACTOR2"
        private const val STASH_ACTOR3: String = "STASH_ACTOR3"
    }
}