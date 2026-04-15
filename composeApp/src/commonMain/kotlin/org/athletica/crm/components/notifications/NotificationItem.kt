package org.athletica.crm.components.notifications

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.athletica.crm.core.ClientId

/**
 * Ссылка внутри уведомления — куда перейти при нажатии на кнопку «Открыть».
 */
sealed interface NotificationLink {
    /** Переход в карточку клиента. */
    data class ToClient(val clientId: ClientId, val clientName: String) : NotificationLink

    /** Переход в раздел «Расписание». */
    data object ToSchedule : NotificationLink

    /** Переход в раздел «Клиенты». */
    data object ToClients : NotificationLink

    /** Переход в раздел «Группы». */
    data object ToGroups : NotificationLink
}

/**
 * Одно уведомление для пользователя.
 *
 * [id] — уникальный идентификатор.
 * [title] — заголовок (краткое описание события).
 * [body] — подробный текст уведомления.
 * [isRead] — прочитано ли уведомление.
 * [createdAt] — момент создания.
 * [link] — опциональная ссылка на раздел приложения.
 */
data class AppNotification(
    val id: Uuid,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Instant,
    val link: NotificationLink? = null,
)
