import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("io.github.smyrgeorge.actor4k.multiplatform.jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val jvmMain by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
                implementation(project(":actor4k-cluster"))
            }
        }
    }
}

application {
    mainClass.set("io.github.smyrgeorge.actor4k.cluster.microbank.MicroBankKt")
}

tasks.withType<ShadowJar> {
    archiveFileName.set("microbank.jar")
}
