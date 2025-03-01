plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
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
                api(libs.log4k)
            }
        }
    }
}
