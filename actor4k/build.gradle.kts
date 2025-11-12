plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform")
    id("io.github.smyrgeorge.actor4k.publish")
    id("io.github.smyrgeorge.actor4k.dokka")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
        jvmMain {
            dependencies {
                api(libs.slf4j.api)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.slf4j.reload4j)
            }
        }
        nativeMain {
            dependencies {
                api(libs.log4k)
            }
        }
        wasmJsMain {
            dependencies {
                api(libs.log4k)
            }
        }
        wasmWasiMain {
            dependencies {
                api(libs.log4k)
            }
        }
    }
}
