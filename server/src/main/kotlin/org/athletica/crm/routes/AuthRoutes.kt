package org.athletica.crm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.LoginRequest
import org.athletica.crm.api.schemas.LoginResponse
import org.athletica.crm.security.JwtConfig
import org.athletica.crm.security.User
import org.athletica.crm.security.UserService

fun Route.authRoutes() {

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val user = UserService.findByCredentials(request.username, request.password)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        call.respondWithJwt(user)
    }

    post("/auth/refresh-token") {
        // заголовок приоритетнее — для desktop/mobile клиентов
        val refreshToken = call.request.header("X-Refresh-Token")
            ?: call.request.cookies[JwtConfig.COOKIE_REFRESH_TOKEN]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Refresh token not provided")

        val decoded = runCatching { JwtConfig.verifier.verify(refreshToken) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid or expired refresh token")

        if (decoded.getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH)
            return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token type")

        val userId = decoded.getClaim(JwtConfig.CLAIM_USER_ID).asInt()
        val user = UserService.findById(userId)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")
        call.respondWithJwt(user)
    }

    authenticate("auth-jwt") {
        get("/auth/me") {
            val principal = call.principal<JWTPrincipal>()!!
            call.respond(
                AuthMeResponse(
                    id = principal.payload.getClaim(JwtConfig.CLAIM_USER_ID).asInt(),
                    username = principal.payload.getClaim(JwtConfig.CLAIM_USERNAME).asString(),
                )
            )
        }
    }
}

suspend fun RoutingCall.respondWithJwt(user: User) {
    val newAccessToken = JwtConfig.makeAccessToken(user.id, user.username)
    val newRefreshToken = JwtConfig.makeRefreshToken(user.id)

    response.cookies.append(
        Cookie(
            name = JwtConfig.COOKIE_ACCESS_TOKEN,
            value = newAccessToken,
            httpOnly = true,
            path = "/",
            extensions = mapOf("SameSite" to "Strict"),
        )
    )
    response.cookies.append(
        Cookie(
            name = JwtConfig.COOKIE_REFRESH_TOKEN,
            value = newRefreshToken,
            httpOnly = true,
            path = "/api/auth/refresh-token",
            extensions = mapOf("SameSite" to "Strict"),
        )
    )

    respond(LoginResponse(accessToken = newAccessToken, refreshToken = newRefreshToken))
}
