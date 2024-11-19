group = "io.github.smyrgeorge"
version = "0.16.0"

// https://mvnrepository.com/artifact/io.grpc/grpc-api
val grpcVersion: String by extra { "1.65.1" }
// https://mvnrepository.com/artifact/com.google.protobuf/protobuf-kotlin
val protobufVersion: String by extra { "4.27.2" }
// https://mvnrepository.com/artifact/io.grpc/grpc-kotlin-stub
val grpcKotlinVersion: String by extra { "1.4.1" }

// Common plugin versions here.
plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.pubhish) apply false
}

repositories {
    mavenCentral()
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        // IMPORTANT: must be last.
        mavenLocal()
    }

    // Dokka config
    run {
        // Exclude microbank.
        if (!project.name.startsWith("actor4k")) return@run
        // Run with ./gradlew :dokkaHtmlMultiModule
        apply(plugin = "org.jetbrains.dokka")
    }
}