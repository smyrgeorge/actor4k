plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
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
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.slf4j.api)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
            }
        }
        val nativeMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }
        val wasmWasiMain by getting {
            dependencies {
                api(libs.log4k)
            }
        }
    }
}
