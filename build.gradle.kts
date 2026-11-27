buildscript {
    // Android и Compose — только для мобильной сборки: сервер и Docker собираются без Android SDK и google().
    if (providers.gradleProperty("athletica.mobile").orNull == "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradlePlugin)
            classpath(libs.compose.gradlePlugin)
            classpath(libs.kotlin.composeCompilerGradlePlugin)
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.6.0")
        filter {
            include("**/src/**")
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        }
    }
}
