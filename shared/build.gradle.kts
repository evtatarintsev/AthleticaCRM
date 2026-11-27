import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(25)
    jvm()

    // Android-таргет — только для мобильной сборки (`-Pathletica.mobile=true`). Классов Android-плагина
    // в обычной сборке нет, поэтому таргет настраивается без типизированного DSL.
    if (providers.gradleProperty("athletica.mobile").orNull == "true") {
        apply(plugin = "com.android.kotlin.multiplatform.library")
        extensions.configure<Any>("android") {
            withGroovyBuilder {
                setProperty("namespace", "org.athletica.crm.shared")
                setProperty("compileSdk", 37)
                setProperty("minSdk", 26)
            }
        }
        tasks.withType<KotlinJvmCompile>().matching { "Android" in it.name }.configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
