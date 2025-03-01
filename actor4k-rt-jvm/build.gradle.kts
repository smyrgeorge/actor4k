plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.jvm")
    id("io.github.smyrgeorge.actor4k.publish")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                api(project(":actor4k"))
                api(libs.slf4j.api)
            }
        }
    }
}
