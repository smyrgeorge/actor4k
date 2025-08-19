plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.binaries")
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
                implementation(project(":actor4k"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
                implementation(project(":actor4k-cluster"))
            }
        }
    }
}
