package io.github.smyrgeorge.actor4k.cluster.grpc

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.objenesis.strategy.StdInstantiatorStrategy
import com.esotericsoftware.kryo.Kryo as KryoSerializer

interface Serde {
    fun <C> encode(value: C): ByteArray
    fun <T> decode(clazz: Class<T>, bytes: ByteArray): T
    fun <T> decode(clazz: String, bytes: ByteArray): T = decode(loadClass(clazz), bytes)

    @Suppress("UNCHECKED_CAST")
    fun <T> loadClass(clazz: String): Class<T> =
        this::class.java.classLoader.loadClass(clazz) as? Class<T>
            ?: error("Could not cast to the requested type")

    class Jackson : Serde {
        private val om: ObjectMapper = create()
        override fun <C> encode(value: C): ByteArray = om.writeValueAsBytes(value)
        override fun <T> decode(clazz: Class<T>, bytes: ByteArray): T = om.readValue(bytes, clazz)

        companion object {
            fun create(): ObjectMapper =
                ObjectMapper().apply {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                    disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                    enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // Timestamps as milliseconds
                    propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
                    setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
                    setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                }
        }
    }

    class Kryo : Serde {
        private val kryo = KryoSerializer().apply {
            isRegistrationRequired = false
            instantiatorStrategy = StdInstantiatorStrategy()
        }

        override fun <C> encode(value: C): ByteArray = Output(4096).use { output ->
            kryo.writeObject(output, value)
            output.toBytes()
        }
        override fun <T> decode(clazz: Class<T>, bytes: ByteArray): T =
            kryo.readObject(Input(bytes), clazz)
    }
}
