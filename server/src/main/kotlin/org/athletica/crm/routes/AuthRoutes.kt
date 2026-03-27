package org.athletica.crm.routes

import arrow.core.Either
import arrow.core.left
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
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse
import org.athletica.crm.api.schemas.SignUpRequest
import org.athletica.crm.core.Lang
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.findByCredentials
import org.athletica.crm.security.findById
import org.athletica.crm.usecases.signUp
import kotlin.uuid.Uuid


/**
 * Регистрирует маршруты аутентификации:
 * POST /auth/login, POST /auth/logout, POST /auth/refresh-token, GET /auth/me.
 * [jwtConfig] — конфигурация JWT для создания и верификации токенов,
 * [signUp] — use case регистрации нового пользователя,
 * [userService] — сервис для работы с пользователями.
 */
context(db: Database, passwordHasher: PasswordHasher)
fun Route.authRoutes(jwtConfig: JwtConfig) {
    postWithContext("/auth/sign-up") {
        val request = call.receive<SignUpRequest>()
        signUp(request)
            .fold(
                { call.respondWithError(it) },
                { call.respondWithJwt(it, jwtConfig) }
            )
    }

    postWithContext("/auth/login") {
        val request = call.receive<LoginRequest>()
        findByCredentials(request.username, request.password)
            .fold(
                { call.respondWithError(it) },
                { call.respondWithJwt(it, jwtConfig) }
            )
    }

    post("/auth/logout") {
        call.response.cookies.setJwtCookies("", "")
        call.respond(HttpStatusCode.OK)
    }

    post("/auth/refresh-token") {
        call.request
            .refreshToken()
            .map {
                jwtConfig.verifyRefreshToken(it).map {
                    it.userIdClaim().map {
                        findById(it)
                            .fold(
                                { call.respondWithError(it) },
                                { call.respondWithJwt(it, jwtConfig) }
                            )
                    }
                }
            }
    }

    authenticate("auth-jwt") {
        getWithContext("/auth/me") {
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


fun ApplicationRequest.refreshToken(): Either<CommonDomainError, String> {
    val token = call.request.header("X-Refresh-Token")
        ?: call.request.cookies[JwtConfig.COOKIE_REFRESH_TOKEN]
    return token?.right() ?: CommonDomainError("", "").left()
}

fun DecodedJWT.userIdClaim(): Either<CommonDomainError, Uuid> {
    if (getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH) {
        return CommonDomainError("", "").left()
    }
    val userId = getClaim(JwtConfig.CLAIM_USER_ID).asString()
    return Uuid.parse(userId).right()
}

/**
 * Формирует JWT токены для [user] и отправляет ответ.
 * Устанавливает HttpOnly cookies для веб-клиентов и возвращает токены в теле ответа.
 * Использует [jwtConfig] для создания токенов.
 */
suspend fun RoutingCall.respondWithJwt(user: AuthenticatedUser, jwtConfig: JwtConfig) {
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
fun ResponseCookies.setJwtCookies(accessToken: String, refreshToken: String) {
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
