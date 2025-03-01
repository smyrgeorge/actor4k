rootProject.name = "actor4k"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("actor4k")
include("actor4k-rt-kmp")
include("actor4k-rt-jvm")
include("examples:jvm")
include("examples:kmp")
