package io.github.smyrgeorge.actor4k.cluster

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
class KotlinxProtobufSerde : Serde {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> encode(clazz: Class<T>, value: Any): ByteArray =
        ProtoBuf.encodeToByteArray(clazz.kotlin.serializer(), value as T)

    override fun <T : Any> decode(clazz: Class<T>, bytes: ByteArray): T =
        ProtoBuf.decodeFromByteArray(clazz.kotlin.serializer(), bytes)
}
