package org.athletica.crm

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.routes.authRoutes
import org.athletica.crm.security.JwtConfig
import java.sql.DriverManager

fun main() {
    runMigrations()
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                val type = credential.payload.getClaim(JwtConfig.CLAIM_TYPE).asString()
                if (type == JwtConfig.TYPE_ACCESS) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token expired or invalid")
            }
        }
    }

    routing {
        route("/api") {
            authRoutes()
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
