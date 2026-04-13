import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleDevtoolsKsp)
}

// KSP для commonMain — генерирует optics для общих моделей
dependencies {
    add("kspCommonMainMetadata", libs.arrow.optics.ksp.plugin)
}

// Сгенерированный код должен быть виден до компиляции других таргетов
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

// ktlint не должен проверять сгенерированный KSP код
listOf(
    org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask::class,
    org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask::class,
).forEach { taskType ->
    tasks.withType(taskType).configureEach {
        mustRunAfter("kspCommonMainKotlinMetadata")
        exclude { it.file.path.contains("build/generated/ksp") }
    }
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            // подключаем сгенерированные optics
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(libs.arrow.core)
                implementation(libs.arrow.optics)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serializationJson)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.serializationKotlinxJson)
                implementation(libs.ktor.client.auth)
            }
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
