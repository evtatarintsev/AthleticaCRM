rootProject.name = "AthleticaCRM"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

/** Мобильное приложение: Android-плагин требует Android SDK уже при конфигурации, поэтому только по свойству. */
val mobile = providers.gradleProperty("athletica.mobile").orNull == "true"

pluginManagement {
    repositories {
        if (providers.gradleProperty("athletica.mobile").orNull == "true") {
            google()
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        if (mobile) {
            google()
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":server")
include(":shared")
if (mobile) {
    include(":mobile", ":mobile:androidApp")
}
