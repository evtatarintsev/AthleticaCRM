package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.notifications.markAllNotificationsRead
import org.athletica.crm.usecases.notifications.markNotificationsRead
import org.athletica.crm.usecases.notifications.notificationList

/**
 * Регистрирует маршруты модуля уведомлений.
 *
 * GET  /notifications             — список уведомлений текущего пользователя.
 * POST /notifications/mark-as-read — отмечает переданные id прочитанными.
 * POST /notifications/mark-all-read — отмечает все уведомления пользователя прочитанными.
 *
 * Query params для GET:
 * - `isRead` (optional): `true` — только прочитанные, `false` — только непрочитанные.
 */
context(db: Database)
fun Route.notificationsRoutes() {
    route("/notifications") {
        getWithContext("") {
            call.eitherToResponse {
                val isRead = call.request.queryParameters["isRead"]?.toBooleanStrictOrNull()
                notificationList(isRead).bind()
            }
        }

        postWithContext("/mark-as-read") {
            call.eitherToResponse {
                val request = call.receive<MarkNotificationsReadRequest>()
                markNotificationsRead(request).bind()
            }
        }

        postWithContext("/mark-all-read") {
            call.eitherToResponse {
                markAllNotificationsRead().bind()
            }
        }
    }
}
