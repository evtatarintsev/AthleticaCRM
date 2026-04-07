package org.athletica.crm.routes

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.notifications.notificationList

/**
 * Регистрирует маршруты модуля уведомлений.
 * GET /notifications — список уведомлений текущего пользователя.
 *
 * Query params:
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
    }
}
