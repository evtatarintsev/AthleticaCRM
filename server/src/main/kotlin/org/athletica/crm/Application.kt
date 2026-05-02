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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.json.Json
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.core.systemContext
import org.athletica.crm.routes.auditRoutes
import org.athletica.crm.routes.authRoutes
import org.athletica.crm.routes.branchesRoutes
import org.athletica.crm.routes.clientsRoutes
import org.athletica.crm.routes.customFieldsRoutes
import org.athletica.crm.routes.disciplinesRoutes
import org.athletica.crm.routes.employeesRoutes
import org.athletica.crm.routes.groupsRoutes
import org.athletica.crm.routes.hallsRoutes
import org.athletica.crm.routes.homeRoutes
import org.athletica.crm.routes.leadSourcesRoutes
import org.athletica.crm.routes.logout
import org.athletica.crm.routes.myBranchesRoute
import org.athletica.crm.routes.notificationsRoutes
import org.athletica.crm.routes.orgRoutes
import org.athletica.crm.routes.profileRoutes
import org.athletica.crm.routes.routeWithContext
import org.athletica.crm.routes.sessionsRoutes
import org.athletica.crm.routes.switchBranchRoute
import org.athletica.crm.routes.uploadRoutes
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.usecases.sessions.generateSessions
import org.athletica.crm.usecases.sessions.generationHorizon
import java.sql.DriverManager
import kotlin.context
import kotlin.uuid.toKotlinUuid

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
        launch {
            di.eventWorker.run()
        }
        launch {
            generateSessionsDaily(di)
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
                context(di.jwtConfig, di.passwordHasher, di.branches) {
                    authRoutes()
                }
                authenticate("auth-jwt") {
                    routeWithContext(di) {
                        logout(di.audit)
                        context(di.branches, di.jwtConfig) {
                            switchBranchRoute()
                        }
                        context(di.branches) {
                            myBranchesRoute()
                        }
                        clientsRoutes(di.clients, di.clientBalances, di.enrollments)
                        groupsRoutes(di.groups, di.disciplines, di.employees, di.sessions, di.bus)
                        sessionsRoutes(di.groups, di.sessions)
                        orgRoutes(di.organizations)
                        branchesRoutes(di.branches)
                        hallsRoutes(di.halls)
                        homeRoutes(di.groups, di.sessions, di.halls)
                        disciplinesRoutes(di.disciplines)
                        leadSourcesRoutes(di.leadSources)
                        customFieldsRoutes(di.customFieldDefinitions)
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

/**
 * Ежедневно генерирует занятия для всех групп на горизонт 8 недель вперёд.
 * Запускается в фоне при старте приложения и повторяется каждые 24 часа.
 */
private suspend fun generateSessionsDaily(di: Di) {
    while (true) {
        try {
            val today = java.time.LocalDate.now().toKotlinLocalDate()
            val horizon = generationHorizon()
            val orgIds =
                di.database.transaction {
                    sql("SELECT DISTINCT org_id FROM groups")
                        .list { row -> row.get("org_id", java.util.UUID::class.java)!!.toKotlinUuid() }
                }
            orgIds.forEach { orgUuid ->
                try {
                    val ctx = systemContext(org.athletica.crm.core.entityids.OrgId(orgUuid))
                    di.database.transaction {
                        arrow.core.raise.either {
                            context(ctx, this@transaction, this) {
                                di.groups.list().forEach { group ->
                                    generateSessions(di.groups, di.sessions, group.id, today, horizon)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to generate sessions for org $orgUuid", e)
                }
            }
        } catch (e: Exception) {
            logger.error("generateSessionsDaily error", e)
        }
        delay(24 * 60 * 60 * 1_000L)
    }
}
