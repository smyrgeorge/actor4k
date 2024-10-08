group = "io.github.smyrgeorge"
version = "0.6.8"

// https://mvnrepository.com/artifact/io.grpc/grpc-api
val grpcVersion: String by extra { "1.65.1" }
// https://mvnrepository.com/artifact/com.google.protobuf/protobuf-kotlin
val protobufVersion: String by extra { "4.27.2" }
// https://mvnrepository.com/artifact/io.grpc/grpc-kotlin-stub
val grpcKotlinVersion: String by extra { "1.4.1" }

// Common plugin versions here.
plugins {
    // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
    kotlin("jvm") version "1.9.25" apply false
    // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.plugin.serialization
    kotlin("plugin.serialization") version "1.9.25" apply false
    // https://github.com/vanniktech/gradle-maven-publish-plugin
    id("com.vanniktech.maven.publish") version "0.29.0" apply false
}
