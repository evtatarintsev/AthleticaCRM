package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import org.athletica.crm.api.schemas.AuthMeResponse
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.auth.changePassword
import org.athletica.crm.usecases.auth.orgInfo
import org.athletica.crm.usecases.auth.profile
import org.athletica.crm.usecases.auth.updateMe

/**
 * Регистрирует маршруты профиля:
 * GET  /auth/me               — возвращает данные текущего авторизованного пользователя.
 * POST /auth/me/update        — обновляет имя и аватар.
 * POST /auth/me/change-password — меняет пароль (требует старый пароль).
 * Требует контекстных параметров [Database], [PasswordHasher], [AuditLog].
 */
context(db: Database, passwordHasher: PasswordHasher, audit: AuditLog)
fun Route.profileRoutes() {
    getWithContext("/auth/me") {
        call.eitherToResponse {
            val user = profile().bind()
            AuthMeResponse(
                id = user.id,
                username = user.username,
                name = user.name,
                avatarId = user.avatarId,
                orgInfo = orgInfo(),
            )
        }
    }

    postWithContext("/auth/me/update") {
        call.eitherToResponse {
            val request = call.receive<UpdateMeRequest>()
            val user = updateMe(request).bind()
            AuthMeResponse(
                id = user.id,
                username = user.username,
                name = user.name,
                avatarId = user.avatarId,
                orgInfo = orgInfo(),
            )
        }
    }

    postWithContext("/auth/me/change-password") {
        call.eitherToResponse {
            val request = call.receive<ChangePasswordRequest>()
            db.transaction {
                changePassword(request)
            }
        }
    }
}
