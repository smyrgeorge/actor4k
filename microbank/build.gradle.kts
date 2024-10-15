import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

java.sourceCompatibility = JavaVersion.VERSION_17

val grpcVersion: String by rootProject.extra

dependencies {
    implementation(project(":actor4k-cluster"))
    api(libs.kotlinx.serialization.protobuf)
    implementation(libs.slf4j.reload4j)
    implementation(libs.jackson.core)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    implementation(platform(libs.http4k.bom))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-netty")
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
    // Resolves: https://stackoverflow.com/questions/55484043/how-to-fix-could-not-find-policy-pick-first-with-google-tts-java-client
    mergeServiceFiles()
}

application {
    mainClass.set("io.github.smyrgeorge.actor4k.microbank.MicroBankKt")
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
