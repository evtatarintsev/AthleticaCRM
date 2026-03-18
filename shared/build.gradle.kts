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
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.clientJs)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
