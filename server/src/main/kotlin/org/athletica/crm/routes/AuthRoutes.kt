package org.athletica.crm.routes

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
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
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.UserService
import org.athletica.crm.usecases.SignUp
import kotlin.uuid.Uuid

/**
 * Регистрирует маршруты аутентификации:
 * POST /auth/login, POST /auth/logout, POST /auth/refresh-token, GET /auth/me.
 *
 * @param jwtConfig конфигурация JWT для создания и верификации токенов
 * @param signUp use case регистрации нового пользователя
 * @param userService сервис для работы с пользователями
 */
fun Route.authRoutes(
    jwtConfig: JwtConfig,
    signUp: SignUp,
    userService: UserService,
) {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val user =
            userService.findByCredentials(request.username, request.password)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        call.respondWithJwt(user, jwtConfig)
    }

    post("/auth/logout") {
        call.response.cookies.setJwtCookies("", "")
        call.respond(HttpStatusCode.OK)
    }

    post("/auth/refresh-token") {
        val refreshToken =
            call.request.header("X-Refresh-Token")
                ?: call.request.cookies[JwtConfig.COOKIE_REFRESH_TOKEN]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Refresh token not provided")

        val decoded =
            runCatching { jwtConfig.verifier.verify(refreshToken) }.getOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid or expired refresh token")

        if (decoded.getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH) {
            return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token type")
        }

        val userId = decoded.getClaim(JwtConfig.CLAIM_USER_ID).asString()
        val user =
            userService.findById(Uuid.parse(userId))
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
        call.respondWithJwt(user, jwtConfig)
    }

    post("/auth/sign-up") {
        val request = call.receive<SignUpRequest>()
        val user = signUp.signUp(request)
        call.respondWithJwt(user, jwtConfig)
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
 * Формирует JWT токены для пользователя и отправляет ответ.
 * Устанавливает HttpOnly cookies для веб-клиентов и возвращает токены в теле ответа.
 *
 * @param user пользователь, для которого создаются токены
 * @param jwtConfig конфигурация JWT для создания токенов
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
 *
 * @param accessToken значение access токена; пустая строка сбрасывает куку
 * @param refreshToken значение refresh токена; пустая строка сбрасывает куку
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
