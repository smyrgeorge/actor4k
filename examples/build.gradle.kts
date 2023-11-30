import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm")
    // https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = rootProject.group
version = rootProject.version
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    // IMPORTANT: must be last.
    mavenLocal()
}

dependencies {
    // Internal dependencies.
    implementation(project(":actor4k"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        // Use "-Xcontext-receivers" to enable context receivers.
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
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

tasks.getByName<Jar>("jar") {
    enabled = false
}

tasks.withType<ShadowJar> {
    // Removes "-all" from the final jar file.
    archiveClassifier.set("")
}

application {
    mainClass.set("io.github.smyrgeorge.actor4k.examples.MainKt")
}

// Resolves warning:
// Reason: Task ':distTar' uses this output of task ':shadowJar' without declaring an explicit or implicit dependency.
tasks.getByName("distTar") {
    dependsOn("shadowJar")
}

// Resolves warning:
// Reason: Task ':distZip' uses this output of task ':shadowJar' without declaring an explicit or implicit dependency.
tasks.getByName("startScripts") {
    dependsOn("shadowJar")
}

// Resolves warning:
// Reason: Task ':startScripts' uses this output of task ':shadowJar' without declaring an explicit or implicit dependency.
tasks.getByName("distZip") {
    dependsOn("shadowJar")
}
