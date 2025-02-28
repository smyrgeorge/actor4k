plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("multiplatform.jvm") {
            id = "io.github.smyrgeorge.actor4k.multiplatform.jvm"
            implementationClass = "io.github.smyrgeorge.actor4k.multiplatform.MultiplatformJvmConventions"
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