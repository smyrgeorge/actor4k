plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.binaries")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                implementation(project(":actor4k"))
            }
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
            }
        }
    }
}
