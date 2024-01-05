package io.github.smyrgeorge.actor4k.cluster.grpc

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

interface Serde {
    fun <T : Any> encode(clazz: Class<T>, value: Any): ByteArray
    fun <T : Any> decode(clazz: Class<T>, bytes: ByteArray): T
    fun <T : Any> decode(clazz: String, bytes: ByteArray): T = decode(loadClass(clazz), bytes)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> loadClass(clazz: String): Class<T> =
        this::class.java.classLoader.loadClass(clazz) as? Class<T>
            ?: error("Could not cast to the requested type")

    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    class KotlinxProtobuf : Serde {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> encode(clazz: Class<T>, value: Any): ByteArray =
            ProtoBuf.encodeToByteArray(clazz.kotlin.serializer(), value as T)

        override fun <T : Any> decode(clazz: Class<T>, bytes: ByteArray): T =
            ProtoBuf.decodeFromByteArray(clazz.kotlin.serializer(), bytes)
    }
}
