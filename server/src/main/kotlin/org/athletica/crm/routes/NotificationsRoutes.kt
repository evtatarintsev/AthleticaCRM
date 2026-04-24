package org.athletica.crm.routes

import io.ktor.client.request.request
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.storage.Database
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
fun RouteWithContext.notificationsRoutes() {
    route("/notifications") {
        get("") {
            call.eitherToResponse {
                val isRead = call.request.queryParameters["isRead"]?.toBooleanStrictOrNull()
                notificationList(isRead).bind()
            }
        }

        post<MarkNotificationsReadRequest, Unit>("/mark-as-read") { request ->
            markNotificationsRead(request).bind()
        }

        post<Unit, Unit>("/mark-all-read") {
            markAllNotificationsRead()
        }
    }
}
