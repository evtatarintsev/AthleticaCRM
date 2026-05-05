package org.athletica.crm.routes

import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.api.schemas.notifications.NotificationsRequest
import org.athletica.crm.api.schemas.notifications.NotificationsResponse
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
 */
context(db: Database)
fun RouteWithContext.notificationsRoutes() {
    route("/notifications") {
        get<NotificationsRequest, NotificationsResponse>("") { request ->
            notificationList(request.isRead).bind()
        }

        post<MarkNotificationsReadRequest, Unit>("/mark-as-read") { request ->
            markNotificationsRead(request).bind()
        }

        post<Unit, Unit>("/mark-all-read") {
            markAllNotificationsRead()
        }
    }
}
