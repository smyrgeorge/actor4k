plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.cluster")
    id("io.github.smyrgeorge.actor4k.publish")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    @Suppress("unused")
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
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
