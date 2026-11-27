plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.athletica.mobile.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.athletica.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"${providers.gradleProperty("athletica.mobile.apiUrl").getOrElse("https://clients.athleticacrm.ru/api/")}\"")
        // TODO: адреса документов уточнить перед публикацией (#275).
        buildConfigField("String", "TERMS_URL", "\"${providers.gradleProperty("athletica.mobile.termsUrl").getOrElse("https://athleticacrm.ru/terms")}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${providers.gradleProperty("athletica.mobile.privacyUrl").getOrElse("https://athleticacrm.ru/privacy")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.mobile)
    implementation(libs.androidx.activity.compose)
}
