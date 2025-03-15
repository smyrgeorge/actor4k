import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("io.github.smyrgeorge.actor4k.multiplatform.binaries")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }

        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                implementation(project(":actor4k-cluster"))
                implementation(libs.kotlinx.serialization.json)
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

application {
    mainClass.set("io.github.smyrgeorge.actor4k.cluster.microbank.MainKt")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("microbank.jar")
}
