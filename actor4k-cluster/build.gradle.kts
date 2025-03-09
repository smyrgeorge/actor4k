plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.krpc)
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
                implementation(libs.kotlinx.serialization.protobuf)
                api(libs.ktor.server.cio)
                api(libs.ktor.client.cio)
                api(libs.krpc.server)
                implementation(libs.krpc.client)
                implementation(libs.krpc.ktor.client)
                implementation(libs.krpc.serialization.protobuf)
            }
        }

        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                implementation(libs.krpc.ktor.server)
                implementation(libs.slf4j.reload4j)
            }
        }
    }
}
