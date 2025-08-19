plugins {
    id("io.github.smyrgeorge.actor4k.multiplatform.binaries")
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
                implementation(project(":actor4k-cluster"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.slf4j.reload4j)
            }
        }
    }
}

tasks.named<Jar>("jvmJar") {
    archiveFileName.set("microbank.jar")

    manifest {
        attributes(
            "Main-Class" to "io.github.smyrgeorge.actor4k.cluster.microbank.MicrobankMainKt"
        )
    }

    // Include dependencies in your JAR (similar to what Shadow does)
    from(configurations.named("jvmRuntimeClasspath").map { config ->
        config.map { if (it.isDirectory) it else zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

