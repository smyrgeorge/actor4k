plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                api(project(":actor4k"))
                api(libs.kotlinx.serialization.protobuf)
                api(libs.ktor.server.cio)
                api(libs.ktor.server.websockets)
                api(libs.ktor.client.cio)
            }
        }
    }
}
