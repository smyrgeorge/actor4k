plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
    alias(libs.plugins.kotlin.serialization)
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
                api(libs.kotlinx.serialization.core)
            }
        }
        @Suppress("unused")
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                api(libs.slf4j.api)
            }
        }
        @Suppress("unused")
        val jvmTest by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
            }
        }
        @Suppress("unused")
        val nativeMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }

        @Suppress("unused")
        val wasmJsMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }

        @Suppress("unused")
        val wasmWasiMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }
    }
}
