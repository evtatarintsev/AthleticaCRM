package org.athletica.crm.api.schemas.notifications

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Ответ на запрос списка уведомлений. */
@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationItem>,
    /** Количество непрочитанных уведомлений — всегда актуально, не зависит от фильтра [isRead]. */
    val unreadCount: Int,
)

/** Одно уведомление пользователя. */
@Serializable
data class NotificationItem(
    val id: Uuid,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Instant,
)

/** Запрос на отметку конкретных уведомлений прочитанными. */
@Serializable
data class MarkNotificationsReadRequest(
    val ids: List<Uuid>,
)
