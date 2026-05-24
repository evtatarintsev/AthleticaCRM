plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.shadow)
    application
}

group = "org.athletica.crm"
version = "1.0.0"

application {
    mainClass.set("org.athletica.crm.admin.MainKt")
}

dependencies {
    implementation(project(":server"))
    implementation(projects.shared)
    implementation(libs.clikt)
    implementation(libs.arrow.core)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.logback)
}
