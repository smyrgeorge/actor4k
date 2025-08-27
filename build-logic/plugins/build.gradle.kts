plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("multiplatform") {
            id = "io.github.smyrgeorge.actor4k.multiplatform"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformConventions"
        }
        create("multiplatform.cluster") {
            id = "io.github.smyrgeorge.actor4k.multiplatform.cluster"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformClusterConventions"
        }
        create("multiplatform.jvm") {
            id = "io.github.smyrgeorge.actor4k.multiplatform.jvm"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformJvmConventions"
        }
        create("multiplatform.binaries") {
            id = "io.github.smyrgeorge.actor4k.multiplatform.binaries"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformBinariesConventions"
        }
        create("publish") {
            id = "io.github.smyrgeorge.actor4k.publish"
            implementationClass = "io.github.smyrgeorge.actor4k.publish.PublishConventions"
        }
        create("dokka") {
            id = "io.github.smyrgeorge.actor4k.dokka"
            implementationClass = "io.github.smyrgeorge.actor4k.dokka.DokkaConventions"
        }
    }
}

dependencies {
    compileOnly(libs.gradle.kotlin.plugin)
    compileOnly(libs.gradle.publish.plugin)
    compileOnly(libs.gradle.dokka.plugin)
}