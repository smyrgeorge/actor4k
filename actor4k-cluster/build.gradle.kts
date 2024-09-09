import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    // https://plugins.gradle.org/plugin/com.google.protobuf
    id("com.google.protobuf") version "0.9.4"
    id("com.vanniktech.maven.publish")
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
    api(project(":actor4k"))
    
    // https://github.com/oshai/kotlin-logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // https://central.sonatype.com/artifact/io.scalecube/scalecube-cluster
    implementation("io.scalecube:scalecube-cluster:2.6.17")
    implementation("io.scalecube:scalecube-transport-netty:2.6.17")

    // https://mvnrepository.com/artifact/com.github.ishugaliy/allgood-consistent-hash
    implementation("com.github.ishugaliy:allgood-consistent-hash:1.0.0")

    // Protobuf
    implementation("io.grpc:grpc-api:$grpcVersion")
    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-protobuf
    // https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-protobuf/kotlinx.serialization.protobuf/
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.3")

    // https://github.com/MicroRaft/MicroRaft
    implementation("io.microraft:microraft:0.7")

    // Test dependencies
    // https://github.com/mockito/mockito-kotlin
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}


// Disable this task, because the protobuf plugin generates too many warnings.
tasks.withType<Javadoc> {
    enabled = false
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
