import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `maven-publish`
    `java-library`
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
    // Kotlin
    // https://github.com/Kotlin/kotlinx.coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")

    // Logging
    api("org.slf4j:slf4j-api:2.0.9")
    api("org.slf4j:slf4j-reload4j:2.0.9")
    // https://github.com/oshai/kotlin-logging
    api("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // Arrow
    // https://github.com/arrow-kt/arrow
    api("io.arrow-kt:arrow-core:1.2.1")
    api("io.arrow-kt:arrow-fx-coroutines:1.2.1")

    // https://central.sonatype.com/artifact/io.scalecube/scalecube-cluster
    api("io.scalecube:scalecube-cluster:2.7.0.rc")
    api("io.scalecube:scalecube-transport-netty:2.7.0.rc")

    // https://mvnrepository.com/artifact/com.github.ishugaliy/allgood-consistent-hash
    api("com.github.ishugaliy:allgood-consistent-hash:1.0.0")

    // Test dependencies
    // https://github.com/mockito/mockito-kotlin
    testApi("org.mockito.kotlin:mockito-kotlin:5.1.0")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = tasks.jar.get().archiveBaseName.get()
        }
    }
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
