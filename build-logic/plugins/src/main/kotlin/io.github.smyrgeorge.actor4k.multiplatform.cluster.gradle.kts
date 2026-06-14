import io.github.smyrgeorge.actor4k.multiplatform.Utils
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    val availableTargets = mapOf(
        "iosX64" to { iosX64() },
        "iosArm64" to { iosArm64() },
        "iosSimulatorArm64" to { iosSimulatorArm64() },
        "androidNativeArm64" to { androidNativeArm64() },
        "androidNativeX64" to { androidNativeX64() },
        "macosArm64" to { macosArm64() },
        "linuxArm64" to { linuxArm64() },
        "linuxX64" to { linuxX64() },
        "mingwX64" to { mingwX64() },
        "jvm" to {
            jvm {
                compilerOptions {
                    freeCompilerArgs.set(listOf("-Xjsr305=strict"))
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        },
    )

    Utils.targetsOf(project).forEach {
        println("Enabling target $it")
        availableTargets[it]?.invoke()
    }

    applyDefaultHierarchyTemplate()
}
