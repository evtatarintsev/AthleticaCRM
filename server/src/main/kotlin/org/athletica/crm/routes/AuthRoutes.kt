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
import org.athletica.crm.security.UserService

fun Route.authRoutes() {

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val user = UserService.findByCredentials(request.username, request.password)

        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        } else {
            call.respond(
                LoginResponse(
                    accessToken = JwtConfig.makeAccessToken(user.id, user.username),
                    refreshToken = JwtConfig.makeRefreshToken(user.id),
                )
            )
        }
    }

    post("/auth/refresh-token") {
        val refreshToken = call.request.header("X-Refresh-Token")
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing X-Refresh-Token header")

        val decoded = runCatching { JwtConfig.verifier.verify(refreshToken) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid or expired refresh token")

        if (decoded.getClaim(JwtConfig.CLAIM_TYPE).asString() != JwtConfig.TYPE_REFRESH)
            return@post call.respond(HttpStatusCode.Unauthorized, "Invalid token type")

        val userId = decoded.getClaim(JwtConfig.CLAIM_USER_ID).asInt()
        val user = UserService.findById(userId)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "User not found")

        call.respond(
            LoginResponse(
                accessToken = JwtConfig.makeAccessToken(user.id, user.username),
                refreshToken = JwtConfig.makeRefreshToken(user.id),
            )
        )
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
