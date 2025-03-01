rootProject.name = "actor4k"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("actor4k")
include("actor4k-kmp")
include("actor4k-jvm")
include("examples:jvm")
include("examples:kmp")
