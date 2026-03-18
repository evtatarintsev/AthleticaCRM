package org.athletica.crm

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.api.schemas.LoginResponse
import org.athletica.crm.api.schemas.AuthMeResponse
import java.sql.DriverManager

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    runMigrations()
    routing {
        route("/api") {
            get("/auth/login") {
                call.respond(LoginResponse("access_token", "refresh_token"))
            }
            get("/auth/me") {
                call.respond(AuthMeResponse(1, "Ivan"))
            }
        }
    }
}

fun runMigrations() {
    val url = System.getenv("POSTGRES_URL")
        ?: throw RuntimeException("Missing POSTGRES_URL environment variable")
    val user = System.getenv("POSTGRES_USER")
        ?: throw RuntimeException("Missing POSTGRES_USER environment variable")
    val password = System.getenv("POSTGRES_PASSWORD")
        ?: throw RuntimeException("Missing POSTGRES_PASSWORD environment variable")

    val connection = DriverManager.getConnection(url, user, password)
    val database = DatabaseFactory.getInstance()
        .findCorrectDatabaseImplementation(JdbcConnection(connection))

    Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database)
        .use { it.update("") }
}
