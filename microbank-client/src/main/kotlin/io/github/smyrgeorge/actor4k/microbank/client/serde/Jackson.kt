package io.github.smyrgeorge.actor4k.microbank.client.serde

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.smyrgeorge.actor4k.cluster.Serde

class Jackson : Serde {
    private val om: ObjectMapper = create()
    override fun <T : Any> encode(clazz: Class<T>, value: Any): ByteArray = om.writeValueAsBytes(value)
    override fun <T : Any> decode(clazz: Class<T>, bytes: ByteArray): T = om.readValue(bytes, clazz)

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
