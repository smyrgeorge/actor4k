plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.cluster")
    id("io.github.smyrgeorge.actor4k.publish")
    id("io.github.smyrgeorge.actor4k.dokka")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
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
