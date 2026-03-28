package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.context.bind
import arrow.core.raise.either
import arrow.core.right
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.ResponseCookies
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse
import org.athletica.crm.api.schemas.SignUpRequest
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.findByCredentials
import org.athletica.crm.security.mapUserById
import org.athletica.crm.usecases.signUp
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты аутентификации:
 * POST /auth/login, POST /auth/logout, POST /auth/refresh-token, GET /auth/me.
 * [jwtConfig] — конфигурация JWT для создания и верификации токенов.
 * Требует контекстных параметров [Database] и [PasswordHasher].
 */
context(db: Database, passwordHasher: PasswordHasher)
fun Route.authRoutes(jwtConfig: JwtConfig) {
    postWithContext("/auth/sign-up") {
        val request = call.receive<SignUpRequest>()
        signUp(request)
            .fold(
                { call.respondWithError(it) },
                { call.respondWithJwt(it, jwtConfig) },
            )
    }

    postWithContext("/auth/login") {
        val request = call.receive<LoginRequest>()
        findByCredentials(request.username, request.password)
            .fold(
                { call.respondWithError(it) },
                { call.respondWithJwt(it, jwtConfig) },
            )
    }

    post("/auth/logout") {
        call.response.cookies.setJwtCookies("", "")
        call.respond(HttpStatusCode.OK)
    }

    post("/auth/refresh-token") {
        either {
            call.request
                .refreshToken().bind()
                .verifiedJwtToken(jwtConfig).bind()
                .userIdClaim().bind()
                .mapUserById().bind()
        }.fold(
            { call.respondWithError(it) },
            { call.respondWithJwt(it, jwtConfig) },
        )
    }

    authenticate("auth-jwt") {
        get("/auth/me") {
            val principal = call.principal<JWTPrincipal>()!!
            call.respond(
                AuthMeResponse(
                    id = principal.payload.getClaim(JwtConfig.CLAIM_USER_ID).asInt(),
                    username = principal.payload.getClaim(JwtConfig.CLAIM_USERNAME).asString(),
                ),
            )
        }
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
fun DecodedJWT.userIdClaim(): Either<CommonDomainError, Uuid> =
    either {
        if (getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH) {
            raise(CommonDomainError("", ""))
        }
        val userId = getClaim(JwtConfig.CLAIM_USER_ID).asString()
        Uuid.parse(userId)
    }

/**
 * Формирует JWT токены для [user] и отправляет ответ.
 * Устанавливает HttpOnly cookies для веб-клиентов и возвращает токены в теле ответа.
 * Использует [jwtConfig] для создания токенов.
 */
suspend fun RoutingCall.respondWithJwt(
    user: AuthenticatedUser,
    jwtConfig: JwtConfig,
) {
    val newAccessToken = jwtConfig.makeAccessToken(user.id, user.username)
    val newRefreshToken = jwtConfig.makeRefreshToken(user.id)

    response.cookies.setJwtCookies(newAccessToken, newRefreshToken)

    respond(LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken))
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
