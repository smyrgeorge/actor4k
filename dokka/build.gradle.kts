plugins {
    id("io.github.smyrgeorge.actor4k.dokka")
}

dependencies {
    dokka(project(":actor4k"))
    dokka(project(":actor4k-cluster"))
}

dokka {
    moduleName.set(rootProject.name)
}
