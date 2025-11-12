plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.binaries")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
            dependencies {
                implementation(project(":actor4k"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.slf4j.reload4j)
                implementation(project(":actor4k-cluster"))
            }
        }
    }
}
