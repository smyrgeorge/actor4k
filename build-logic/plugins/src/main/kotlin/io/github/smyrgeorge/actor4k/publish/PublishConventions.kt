package io.github.smyrgeorge.actor4k.publish

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.util.*

@Suppress("unused")
class PublishConventions : Plugin<Project> {

    private val descriptions: Map<String, String> = mapOf(
        "actor4k" to "A small actor system written in kotlin using Coroutines (kotlinx.coroutines).",
        "actor4k-cluster" to "A small actor system written in kotlin using Coroutines (kotlinx.coroutines).",
    )

    private fun Project.loadPublishProperties() {
        val local = Properties()
        File(project.rootProject.rootDir, "local.properties").also {
            if (!it.exists()) return@loadPublishProperties
            it.inputStream().use { s -> local.load(s) }
        }

        // Set Maven Central credentials as project properties
        local.getProperty("mavenCentralUsername")?.let { project.extra["mavenCentralUsername"] = it }
        local.getProperty("mavenCentralPassword")?.let { project.extra["mavenCentralPassword"] = it }

        // Set signing properties as project properties
        local.getProperty("signing.keyId")?.let { project.extra["signing.keyId"] = it }
        local.getProperty("signing.password")?.let { project.extra["signing.password"] = it }
        local.getProperty("signing.secretKeyRingFile")?.let { project.extra["signing.secretKeyRingFile"] = it }
    }

    override fun apply(project: Project) {
        project.plugins.apply("com.vanniktech.maven.publish")
        project.extensions.configure<MavenPublishBaseExtension> {
            // sources publishing is always enabled by the Kotlin Multiplatform plugin
            configure(
                KotlinMultiplatform(
                    // whether to publish a sources jar
                    sourcesJar = true,
                    // configures the -javadoc artifact, possible values:
                    javadocJar = JavadocJar.Dokka("dokkaHtml"),
                )
            )
            coordinates(
                groupId = project.group as String,
                artifactId = project.name,
                version = project.version as String
            )

            pom {
                name.set(project.name)
                description.set(descriptions[project.name] ?: error("Missing description for $project.name"))
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
    }
}
