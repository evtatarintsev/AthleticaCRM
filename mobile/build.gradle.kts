import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(25)
    android {
        namespace = "org.athletica.mobile"
        compileSdk = 37
        minSdk = 26
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        androidResources.enable = true
        withDeviceTestBuilder {}.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    // shortcut: JVM-таргет только для тестов экранов (`runComposeUiTest` без эмулятора).
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "AthleticaMobile"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.compose.resources)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.datastore)
            implementation(libs.tink.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.uiTest)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.testJunit)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

compose.resources {
    packageOfResClass = "org.athletica.mobile.resources"
}
