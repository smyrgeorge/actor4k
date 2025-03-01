plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                implementation(project(":actor4k-jvm"))
                implementation(libs.slf4j.reload4j)
            }
        }
    }
}
