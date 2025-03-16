group = "io.github.smyrgeorge"
version = "0.50.0"

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.pubhish) apply false
}

repositories {
    mavenCentral()
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        // IMPORTANT: must be last.
        mavenLocal()
    }

    // Dokka config
    run {
        if (!project.name.startsWith("actor4k")) return@run
        // Run with ./gradlew :dokkaHtmlMultiModule
        apply(plugin = "org.jetbrains.dokka")
    }
}