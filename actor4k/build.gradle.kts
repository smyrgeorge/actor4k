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
                implementation(kotlin("reflect"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.coroutines.reactive)
                api(libs.arrow.core)
                api(libs.arrow.fx.coroutines)
            }
        }
    }
}
