plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "org.athletica.crm"
version = "1.0.0"
application {
    mainClass.set("org.athletica.crm.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.arrow.core)
    implementation(libs.clikt)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.netty)
    implementation(libs.liquibase.core)
    implementation(libs.postgresql.jdbc)
    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bouncycastle)
    implementation(libs.minio)
    implementation(libs.angus.mail)
    implementation(libs.sentry.logback)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.commons.csv)
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Генератор контрактов веб-клиента. Отдельный source set: в jar сервера не попадает,
 * видит код сервера и его зависимости.
 */
val contracts: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[contracts.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[contracts.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    "contractsImplementation"(libs.ktor.server.testHost)
    testImplementation(contracts.output)
}

/** Генерирует `web/src/api/generated/contracts.ts` из маршрутов сервера и схем `shared`. */
tasks.register<JavaExec>("generateWebContracts") {
    group = "build"
    description = "Генерирует контракты API веб-клиента (web/src/api/generated/contracts.ts)"
    classpath = contracts.runtimeClasspath
    mainClass.set("org.athletica.crm.contracts.GenerateWebContractsKt")
    args(rootProject.file("web/src/api/generated/contracts.ts").absolutePath)
}

tasks.named<Jar>("shadowJar") {
    archiveFileName.set("athletica.jar")
}
