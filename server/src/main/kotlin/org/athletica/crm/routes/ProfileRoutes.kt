package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.profile
import org.athletica.crm.usecases.updateMe

/**
 * Регистрирует маршруты профиля:
 * GET /auth/me — возвращает данные текущего авторизованного пользователя.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun Route.profileRoutes() {
    getWithContext("/auth/me") {
        call.eitherToResponse {
            val user = profile().bind()
            AuthMeResponse(
                id = user.id.toString(),
                username = user.username,
                name = user.name,
                avatarId = user.avatarId,
            )
        }
    }

    postWithContext("/auth/me/update") {
        call.eitherToResponse {
            val request = call.receive<UpdateMeRequest>()
            val user = updateMe(request).bind()
            AuthMeResponse(
                id = user.id.toString(),
                username = user.username,
                name = user.name,
                avatarId = user.avatarId,
            )
        }
    }
}
