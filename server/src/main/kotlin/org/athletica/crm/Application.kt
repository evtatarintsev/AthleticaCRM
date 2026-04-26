package org.athletica.crm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.routes.auditRoutes
import org.athletica.crm.routes.authRoutes
import org.athletica.crm.routes.clientsRoutes
import org.athletica.crm.routes.disciplinesRoutes
import org.athletica.crm.routes.employeesRoutes
import org.athletica.crm.routes.groupsRoutes
import org.athletica.crm.routes.logout
import org.athletica.crm.routes.notificationsRoutes
import org.athletica.crm.routes.orgRoutes
import org.athletica.crm.routes.profileRoutes
import org.athletica.crm.routes.routeWithContext
import org.athletica.crm.routes.uploadRoutes
import org.athletica.crm.security.JwtConfig
import java.sql.DriverManager
import kotlin.context

private val logger = KtorSimpleLogger("org.athletica.crm.Application")

/** Точка входа: делегирует запуск Ktor [EngineMain], который читает application.conf. */
fun main(args: Array<String>): Unit = EngineMain.main(args)

/** Модуль приложения: инициализирует конфигурацию, запускает миграции и настраивает плагины. */
fun Application.module() {
    val di = di()

    runMigrations(di.databaseConfig)

    CoroutineScope(Dispatchers.Default + SupervisorJob()).apply {
        launch {
            di.emailDispatcher.dispatchPending()
        }
        monitor.subscribe(ApplicationStopped) {
            cancel()
        }
    }

    context(di) {
        configureServer()
    }
}

/**
 * Устанавливает плагины и маршруты без запуска миграций.
 * Выделена отдельно для возможности тестирования без подключения к БД.
 */
context(di: Di)
fun Application.configureServer() {
    install(CallLogging)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(code = "INTERNAL_ERROR", message = "Something went wrong"),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(code = "NOT_FOUND", message = "Resource not found"),
            )
        }
    }

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                call.request
                    .header(HttpHeaders.Authorization)
                    ?.let { parseAuthorizationHeader(it) }
                    ?: call.request.cookies[JwtConfig.COOKIE_ACCESS_TOKEN]
                        ?.let { HttpAuthHeader.Single("Bearer", it) }
            }
            verifier(di.jwtConfig.verifier)
            validate { credential ->
                val type = credential.payload.getClaim(JwtConfig.CLAIM_TYPE).asString()
                if (type == JwtConfig.TYPE_ACCESS) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token expired or invalid")
            }
        }
    }
    context(di.database, di.audit) {
        routing {
            route("/api") {
                context(di.jwtConfig, di.passwordHasher) {
                    authRoutes()
                }
                authenticate("auth-jwt") {
                    routeWithContext(di) {
                        logout(di.audit)
                        clientsRoutes(di.clients, di.clientBalances, di.enrollments)
                        groupsRoutes(di.groups, di.disciplines)
                        orgRoutes(di.organizations)
                        disciplinesRoutes(di.disciplines)
                        employeesRoutes(di.employees, di.roles)
                        context(di.passwordHasher) {
                            profileRoutes(di.organizations, di.orgBalances)
                        }
                        context(di.minio) {
                            uploadRoutes()
                        }
                        auditRoutes(di.audit)
                        notificationsRoutes()
                    }
                }
            }
        }
    }
}

/**
 * Запускает Liquibase миграции базы данных.
 * [url] — JDBC URL подключения к PostgreSQL, [user] и [password] — учётные данные пользователя БД.
 */
fun runMigrations(
    url: String,
    user: String,
    password: String,
) {
    val connection = DriverManager.getConnection(url, user, password)
    val database =
        DatabaseFactory
            .getInstance()
            .findCorrectDatabaseImplementation(JdbcConnection(connection))

    Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database)
        .use { it.update("") }
}

fun runMigrations(dbConfig: DatabaseConfig) = runMigrations(dbConfig.url, dbConfig.user, dbConfig.password)
