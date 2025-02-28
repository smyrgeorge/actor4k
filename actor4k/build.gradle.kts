import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pubhish)
}

java.sourceCompatibility = JavaVersion.VERSION_21

dependencies {
    implementation(kotlin("reflect"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.reactive)
    api(libs.arrow.core)
    api(libs.arrow.fx.coroutines)
    api(libs.slf4j.api)

    // Test dependencies
    testImplementation(libs.mockito)
}

mavenPublishing {
    coordinates(
        groupId = group as String,
        artifactId = name,
        version = version as String
    )

    pom {
        name = "actor4k"
        description = "A small actor system written in kotlin using Coroutines (kotlinx.coroutines)."
        url = "https://github.com/smyrgeorge/actor4k"

        licenses {
            license {
                name = "MIT License"
                url = "https://github.com/smyrgeorge/actor4k/blob/main/LICENSE"
            }
        }

        developers {
            developer {
                id = "smyrgeorge"
                name = "Yorgos S."
                email = "smyrgoerge@gmail.com"
                url = "https://smyrgeorge.github.io/"
            }
        }

        scm {
            url = "https://github.com/smyrgeorge/actor4k"
            connection = "scm:git:https://github.com/smyrgeorge/actor4k.git"
            developerConnection = "scm:git:git@github.com:smyrgeorge/actor4k.git"
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Enable GPG signing for all publications
    signAllPublications()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        // Use "-Xcontext-receivers" to enable context receivers.
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Log each test.
    testLogging { events = setOf(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED) }

    // Print a summary after test suite.
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            // Wll match the outermost suite.
            if (suite.parent == null) {
                println("\nTest result: ${result.resultType}")
                val summary = "Test summary: ${result.testCount} tests, " +
                        "${result.successfulTestCount} succeeded, " +
                        "${result.failedTestCount} failed, " +
                        "${result.skippedTestCount} skipped"
                println(summary)
            }
        }
    })
}
