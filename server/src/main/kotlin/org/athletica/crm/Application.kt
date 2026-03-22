package org.athletica.crm

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.serialization.json.Json
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.athletica.crm.db.Database
import org.athletica.crm.routes.authRoutes
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.UserService
import org.athletica.crm.usecases.SignUp
import java.sql.DriverManager

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
    val userService = UserService(database)

    val corsAllowedHosts = config.property("cors.allowedHosts").getString()
    configureServer(jwtConfig, userService, corsAllowedHosts)
}

/**
 * Устанавливает плагины и маршруты без запуска миграций.
 * Выделена отдельно для возможности тестирования без подключения к БД.
 *
 * @param jwtConfig конфигурация JWT токенов
 * @param userService сервис пользователей
 * @param corsAllowedHost хост, которому разрешены кросс-доменные запросы (например, `localhost:8081`)
 */
fun Application.configureServer(
    jwtConfig: JwtConfig,
    userService: UserService,
    corsAllowedHost: String = "localhost:8081",
) {
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
                call.request.header(HttpHeaders.Authorization)
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
            authRoutes(jwtConfig, SignUp(), userService)
        }
    }
}

/**
 * Создаёт [Database] с R2DBC пулом соединений.
 * JDBC URL автоматически преобразуется в R2DBC URL.
 *
 * @param jdbcUrl JDBC URL вида `jdbc:postgresql://host:port/db`
 * @param user имя пользователя БД
 * @param password пароль пользователя БД
 */
fun createDatabase(jdbcUrl: String, user: String, password: String): Database {
    val r2dbcUrl = jdbcUrl.replace("jdbc:postgresql", "r2dbc:postgresql")
    val options =
        ConnectionFactoryOptions.parse(r2dbcUrl)
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
 *
 * @param url JDBC URL подключения к PostgreSQL
 * @param user имя пользователя БД
 * @param password пароль пользователя БД
 */
fun runMigrations(
    url: String,
    user: String,
    password: String,
) {
    val connection = DriverManager.getConnection(url, user, password)
    val database =
        DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(JdbcConnection(connection))

    Liquibase("db/changelog/db.changelog-master.yaml", ClassLoaderResourceAccessor(), database)
        .use { it.update("") }
}
