group = "io.github.smyrgeorge"
version = "0.1.0"

val grpcVersion: String by extra { "1.60.1" }
val protobufVersion: String by extra { "3.25.1" }
val grpcKotlinVersion: String by extra { "1.4.1" }

// Common plugin versions here.
plugins {
    // NOTE: we use [apply] false.
    // https://docs.gradle.org/current/userguide/plugins.html#sec:subprojects_plugins_dsl
    // https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
    kotlin("jvm") version "1.9.0" apply false
}
