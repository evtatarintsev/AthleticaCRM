package org.athletica.crm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.minio.MinioClient
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.PostgresAuditLog
import org.athletica.crm.db.Database
import org.athletica.crm.domain.discipline.DbDisciplines
import org.athletica.crm.routes.auditRoutes
import org.athletica.crm.routes.authRoutes
import org.athletica.crm.routes.clientsRoutes
import org.athletica.crm.routes.disciplinesRoutes
import org.athletica.crm.routes.employeesRoutes
import org.athletica.crm.routes.groupsRoutes
import org.athletica.crm.routes.notificationsRoutes
import org.athletica.crm.routes.orgRoutes
import org.athletica.crm.routes.profileRoutes
import org.athletica.crm.routes.uploadRoutes
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.MinioService
import org.athletica.infra.mail.Mailbox
import org.athletica.infra.mail.SmtpConfig
import org.athletica.infra.mail.SmtpMailbox
import java.sql.DriverManager

private val logger = KtorSimpleLogger("org.athletica.crm.Application")

/** Точка входа: делегирует запуск Ktor [EngineMain], который читает application.conf. */
fun main(args: Array<String>): Unit = EngineMain.main(args)

/** Модуль приложения: инициализирует конфигурацию, запускает миграции и настраивает плагины. */
fun Application.module() {
    val config = environment.config

    val jwtConfig =
        JwtConfig(
            secret = config.property("jwt.secret").getString(),
            accessTokenTtlMinutes = config.property("jwt.accessTokenTtlMinutes").getString().toLong(),
            refreshTokenTtlDays = config.property("jwt.refreshTokenTtlDays").getString().toLong(),
        )

    val dbUrl = config.property("database.url").getString()
    val dbUser = config.property("database.user").getString()
    val dbPassword = config.property("database.password").getString()

    runMigrations(url = dbUrl, user = dbUser, password = dbPassword)

    val database = createDatabase(jdbcUrl = dbUrl, user = dbUser, password = dbPassword)
    val corsAllowedHosts = config.property("cors.allowedHosts").getString()

    val minioService =
        MinioService(
            client =
                MinioClient
                    .builder()
                    .endpoint(config.property("minio.endpoint").getString())
                    .credentials(
                        config.property("minio.accessKey").getString(),
                        config.property("minio.secretKey").getString(),
                    ).build(),
            bucket = config.property("minio.bucket").getString(),
        ).also { it.ensureBucketExists() }

    val auditScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val auditService = PostgresAuditLog(database, auditScope)
    monitor.subscribe(ApplicationStopped) { auditService.close() }

    context(database, PasswordHasher(), minioService, mailbox()) {
        context(auditService) {
            configureServer(jwtConfig, corsAllowedHosts)
        }
    }
}

/**
 * Устанавливает плагины и маршруты без запуска миграций.
 * Выделена отдельно для возможности тестирования без подключения к БД.
 * [jwtConfig] — конфигурация JWT токенов,
 * [corsAllowedHost] — хост для кросс-доменных запросов (например, `localhost:8081`).
 * Требует контекстных параметров [Database] и [PasswordHasher].
 */
context(db: Database, passwordHasher: PasswordHasher, minioService: MinioService, audit: AuditLog, mailbox: Mailbox)
fun Application.configureServer(
    jwtConfig: JwtConfig,
    corsAllowedHost: String = "localhost:8081",
) {
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

    install(CORS) {
        allowHost(corsAllowedHost, schemes = listOf("http", "https"))
        allowCredentials = true
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
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
            verifier(jwtConfig.verifier)
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
            context(jwtConfig) {
                authRoutes()
            }
            authenticate("auth-jwt") {
                clientsRoutes()
                groupsRoutes()
                orgRoutes()
                disciplinesRoutes(DbDisciplines(db, audit))
                employeesRoutes()
                profileRoutes()
                uploadRoutes()
                auditRoutes()
                notificationsRoutes()
            }
        }
    }
}

/**
 * Создаёт [Mailbox] на основе SMTP-настроек из конфигурации.
 */
fun Application.mailbox(): Mailbox =
    SmtpMailbox(
        SmtpConfig(
            host = environment.config.property("smtp.host").getString(),
            port = environment.config.property("smtp.port").getString().toInt(),
            username = environment.config.property("smtp.username").getString(),
            password = environment.config.property("smtp.password").getString(),
            fromAddress = environment.config.property("smtp.fromAddress").getString(),
            fromName = environment.config.property("smtp.fromName").getString(),
        ),
    )

/**
 * Создаёт [Database] с R2DBC пулом соединений.
 * JDBC URL автоматически преобразуется в R2DBC URL.
 * [jdbcUrl] — JDBC URL вида `jdbc:postgresql://host:port/db`,
 * [user] и [password] — учётные данные пользователя БД.
 */
fun createDatabase(
    jdbcUrl: String,
    user: String,
    password: String,
): Database {
    val r2dbcUrl = jdbcUrl.replace("jdbc:postgresql", "r2dbc:postgresql")
    val options =
        ConnectionFactoryOptions
            .parse(r2dbcUrl)
            .mutate()
            .option(ConnectionFactoryOptions.USER, user)
            .option(ConnectionFactoryOptions.PASSWORD, password)
            .build()
    val pool =
        ConnectionPool(
            ConnectionPoolConfiguration
                .builder(ConnectionFactories.get(options))
                .initialSize(2)
                .maxSize(10)
                .build(),
        )
    return Database(pool)
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
