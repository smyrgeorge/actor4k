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
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
            }
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                api(libs.slf4j.api)
            }
        }
        @Suppress("unused")
        val nativeMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }
    }
}
