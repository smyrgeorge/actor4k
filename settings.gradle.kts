rootProject.name = "actor4k"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("actor4k")
include("actor4k-cluster")

include("examples")
include("microbank")
include("microbank-client")
