import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
}

val descriptions = mapOf(
    "actor4k" to "A small actor system written in kotlin using Coroutines (kotlinx.coroutines).",
    "actor4k-cluster" to "A small actor system written in kotlin using Coroutines (kotlinx.coroutines).",
)

configure<MavenPublishBaseExtension> {
    // sources publishing is always enabled by the Kotlin Multiplatform plugin
    configure(
        KotlinMultiplatform(
            // whether to publish a sources jar
            sourcesJar = SourcesJar.Sources()
        )
    )
    coordinates(
        groupId = project.group as String,
        artifactId = project.name,
        version = project.version as String
    )

    pom {
        name.set(project.name)
        description.set(descriptions[project.name] ?: error("Missing description for ${project.name}"))
        url.set("https://github.com/smyrgeorge/actor4k")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/smyrgeorge/actor4k/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("smyrgeorge")
                name.set("Yorgos S.")
                email.set("smyrgoerge@gmail.com")
                url.set("https://smyrgeorge.github.io/")
            }
        }

        scm {
            url.set("https://github.com/smyrgeorge/actor4k")
            connection.set("scm:git:https://github.com/smyrgeorge/actor4k.git")
            developerConnection.set("scm:git:git@github.com:smyrgeorge/actor4k.git")
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral()

    // Enable GPG signing for all publications
    signAllPublications()
}
