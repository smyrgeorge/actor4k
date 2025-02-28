package io.github.smyrgeorge.actor4k.multiplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class MultiplatformJvmConventions : Plugin<Project> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(project: Project) {
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                withJava()
                compilerOptions {
                    freeCompilerArgs.set(listOf("-Xjsr305=strict"))
                    jvmTarget.set(JvmTarget.JVM_21)
                }

                project.tasks.withType<Test> {
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
            }
            applyDefaultHierarchyTemplate()
        }
    }
}
