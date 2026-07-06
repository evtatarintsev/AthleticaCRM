package org.athletica.crm.routes

import org.athletica.crm.api.schemas.notifications.MarkNotificationsReadRequest
import org.athletica.crm.api.schemas.notifications.NotificationItem
import org.athletica.crm.api.schemas.notifications.NotificationsRequest
import org.athletica.crm.api.schemas.notifications.NotificationsResponse
import org.athletica.crm.core.entityids.toNotificationId
import org.athletica.crm.domain.notifications.Notification
import org.athletica.crm.domain.notifications.Notifications
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты модуля уведомлений.
 *
 * GET  /notifications              — список уведомлений текущего сотрудника.
 * POST /notifications/mark-as-read — отмечает переданные id прочитанными.
 * POST /notifications/mark-all-read — отмечает все уведомления сотрудника прочитанными.
 */
context(db: Database)
fun RouteWithContext.notificationsRoutes(notifications: Notifications) {
    route("/notifications") {
        get<NotificationsRequest, NotificationsResponse>("") { request ->
            db.transaction {
                NotificationsResponse(
                    notifications = notifications.of(request.isRead).map { it.toItem() },
                    unreadCount = notifications.unreadCount(),
                )
            }
        }

        post<MarkNotificationsReadRequest, Unit>("/mark-as-read") { request ->
            db.transaction {
                notifications.markAsRead(request.ids.map { it.toNotificationId() })
            }
        }

        post<Unit, Unit>("/mark-all-read") {
            db.transaction {
                notifications.markAllAsRead()
            }
        }
    }
}

/** Преобразует доменное [Notification] в схему ответа [NotificationItem]. */
private fun Notification.toItem() =
    NotificationItem(
        id = id.value,
        title = title,
        body = body,
        isRead = isRead,
        createdAt = createdAt,
    )
