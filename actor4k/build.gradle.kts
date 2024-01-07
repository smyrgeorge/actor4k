import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
    // https://plugins.gradle.org/plugin/com.google.protobuf
    id("com.google.protobuf") version "0.9.4"
}

group = rootProject.group
version = rootProject.version
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    // IMPORTANT: must be last.
    mavenLocal()
}

val grpcVersion: String by rootProject.extra
val protobufVersion: String by rootProject.extra
val grpcKotlinVersion: String by rootProject.extra

dependencies {
    // Kotlin
    // https://github.com/Kotlin/kotlinx.coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")

    // Logging
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    api("org.slf4j:slf4j-api:2.0.10")
    api("org.slf4j:slf4j-reload4j:2.0.10")
    // https://github.com/oshai/kotlin-logging
    api("io.github.oshai:kotlin-logging-jvm:6.0.1")

    // Arrow
    // https://github.com/arrow-kt/arrow
    api("io.arrow-kt:arrow-core:1.2.1")
    api("io.arrow-kt:arrow-fx-coroutines:1.2.1")

    // https://central.sonatype.com/artifact/io.scalecube/scalecube-cluster
    api("io.scalecube:scalecube-cluster:2.6.17")
    api("io.scalecube:scalecube-transport-netty:2.6.17")

    // https://mvnrepository.com/artifact/com.github.ishugaliy/allgood-consistent-hash
    api("com.github.ishugaliy:allgood-consistent-hash:1.0.0")

    // Protobuf
    api("io.grpc:grpc-api:$grpcVersion")
    api("io.grpc:grpc-netty:$grpcVersion")
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    api("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    // https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-protobuf
    api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.2")

    //https://github.com/MicroRaft/MicroRaft
    api("io.microraft:microraft:0.6")

    // Test dependencies
    // https://github.com/mockito/mockito-kotlin
    testApi("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

java {
    withSourcesJar()
}

publishing {
//    repositories {
//        maven {
//            name = "OSSRH"
//            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
//            credentials {
//                username = System.getenv("MAVEN_USERNAME")
//                password = System.getenv("MAVEN_PASSWORD")
//            }
//        }
//    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/io.github.smyrgeorge/actor4k")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
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

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
