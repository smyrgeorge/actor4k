plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("multiplatform") {
            id = "io.github.smyrgeorge.actor4k.multiplatform"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformConventions"
        }
        create("multiplatform.binaries") {
            id = "io.github.smyrgeorge.actor4k.multiplatform.binaries"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformBinariesConventions"
        }
        create("publish") {
            id = "io.github.smyrgeorge.actor4k.publish"
            implementationClass = "io.github.smyrgeorge.actor4k.publish.PublishConventions"
        }
    }
}

dependencies {
    compileOnly(libs.gradle.kotlin.plugin)
    compileOnly(libs.gradle.publish.plugin)
}