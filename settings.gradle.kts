rootProject.name = "actor4k"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("actor4k")
include("actor4k-java")
include("examples")
