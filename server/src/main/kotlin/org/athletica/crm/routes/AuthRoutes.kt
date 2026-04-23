package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.right
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.ResponseCookies
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.post
import org.athletica.crm.api.schemas.ErrorResponse
import org.athletica.crm.api.schemas.auth.LoginRequest
import org.athletica.crm.api.schemas.auth.LoginResponse
import org.athletica.crm.api.schemas.auth.SignUpRequest
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logLogin
import org.athletica.crm.domain.audit.logSignUp
import org.athletica.crm.domain.audit.logout
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.findByCredentials
import org.athletica.crm.security.mapUserById
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.auth.signUp
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты аутентификации:
 * POST /auth/login, POST /auth/logout, POST /auth/refresh-token, GET /auth/me.
 * [jwtConfig] — конфигурация JWT для создания и верификации токенов.
 * Требует контекстных параметров [Database], [PasswordHasher] и [AuditLog].
 */
context(db: Database, passwordHasher: PasswordHasher, jwtConfig: JwtConfig, audit: AuditLog)
fun Route.authRoutes() {
    post("/auth/sign-up") {
        call.eitherToAuthResponse {
            val request = call.receive<SignUpRequest>()
            db.transaction {
                signUp(request).bind()
                    .also {
                        audit.logSignUp(
                            it.orgId, it.id, it.username, call.clientIp(),
                        )
                    }
            }
        }
    }

    post("/auth/login") {
        call.eitherToAuthResponse {
            val request = call.receive<LoginRequest>()
            db.transaction {
                findByCredentials(request.username, request.password).bind()
                    .also {
                        audit.logLogin(it.orgId, it.id, it.username, call.clientIp())
                    }
            }
        }
    }

    postWithContext("/auth/logout") {
        db.transaction {
            audit.logout()
        }
        call.response.cookies.setJwtCookies("", "")
        call.respond(HttpStatusCode.OK)
    }

    post("/auth/refresh-token") {
        call.eitherToAuthResponse {
            db.transaction {
                call.request
                    .refreshToken().bind()
                    .verifiedJwtToken(jwtConfig).bind()
                    .userIdClaim().bind()
                    .mapUserById()
            }
        }
    }
}

/**
 * Выполняет [block] в контексте [Raise], при успехе выпускает новые JWT-токены и отвечает [LoginResponse].
 * При ошибке отвечает HTTP 400 с [ErrorResponse].
 */
context(jwtConfig: JwtConfig)
suspend inline fun RoutingCall.eitherToAuthResponse(block: Raise<DomainError>.() -> AuthenticatedUser) {
    either {
        val user = block()
        val newAccessToken = jwtConfig.makeAccessToken(user)
        val newRefreshToken = jwtConfig.makeRefreshToken(user.id)

        response.cookies.setJwtCookies(newAccessToken, newRefreshToken)
        respond(LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken))
    }.onLeft {
        respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(code = it.code, message = it.message),
        )
    }
}

/**
 * Верифицирует JWT-токен с помощью [jwtConfig].
 * Возвращает декодированный токен или ошибку, если подпись невалидна либо токен истёк.
 */
fun String.verifiedJwtToken(jwtConfig: JwtConfig): Either<CommonDomainError, DecodedJWT> =
    runCatching { jwtConfig.verifier.verify(this) }
        .getOrNull()
        ?.right() ?: CommonDomainError("", "").left()

/**
 * Извлекает refresh-токен из запроса.
 * Сначала ищет в заголовке `X-Refresh-Token`, затем в cookie [JwtConfig.COOKIE_REFRESH_TOKEN].
 * Возвращает токен или ошибку, если он отсутствует.
 */
fun ApplicationRequest.refreshToken(): Either<CommonDomainError, String> =
    either {
        val token =
            call.request.header("X-Refresh-Token")
                ?: call.request.cookies[JwtConfig.COOKIE_REFRESH_TOKEN]
        if (token == null) {
            raise(CommonDomainError("", ""))
        }
        token
    }

/**
 * Извлекает идентификатор пользователя из claim-а токена.
 * Проверяет, что тип токена — [JwtConfig.TYPE_REFRESH], иначе возвращает ошибку.
 */
fun DecodedJWT.userIdClaim(): Either<CommonDomainError, UserId> =
    either {
        if (getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH) {
            raise(CommonDomainError("", ""))
        }
        val userId = getClaim(JwtConfig.CLAIM_USER_ID).asString()
        Uuid.parse(userId).toUserId()
    }

/**
 * Устанавливает HttpOnly cookies с JWT токенами.
 * При передаче пустых строк браузер удаляет соответствующие cookies (используется при logout).
 * [accessToken] — значение access токена; пустая строка сбрасывает куку.
 * [refreshToken] — значение refresh токена; пустая строка сбрасывает куку.
 */
fun ResponseCookies.setJwtCookies(
    accessToken: String,
    refreshToken: String,
) {
    append(
        Cookie(
            name = JwtConfig.COOKIE_ACCESS_TOKEN,
            value = accessToken,
            httpOnly = true,
            path = "/",
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
    append(
        Cookie(
            name = JwtConfig.COOKIE_REFRESH_TOKEN,
            value = refreshToken,
            httpOnly = true,
            path = "/api/auth/refresh-token",
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}
