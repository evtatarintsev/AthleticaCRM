import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.ktor.client.auth)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
