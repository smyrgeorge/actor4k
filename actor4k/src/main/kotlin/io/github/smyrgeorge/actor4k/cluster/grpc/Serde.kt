package io.github.smyrgeorge.actor4k.cluster.grpc

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

interface Serde {
    fun <C> encode(value: C): ByteArray
    fun <T> decode(clazz: Class<*>, bytes: ByteArray): T
    fun loadClass(clazz: String): Class<*>

    class Json : Serde {
        private val om: ObjectMapper = create()
        override fun <C> encode(value: C): ByteArray = om.writeValueAsBytes(value)

        @Suppress("UNCHECKED_CAST")
        override fun <T> decode(clazz: Class<*>, bytes: ByteArray): T =
            om.readValue(bytes, clazz) as? T ?: error("Could not cast to the requested type")

        override fun loadClass(clazz: String): Class<*> = this::class.java.classLoader.loadClass(clazz)

        private fun create(): ObjectMapper =
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
