package org.athletica.crm.api.schemas.notifications

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.GroupId
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Параметры запроса списка уведомлений с опциональным фильтром по статусу прочтения. */
@Serializable
data class NotificationsRequest(
    val isRead: Boolean? = null,
)

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
    /** Объект уведомления, на который ведёт ссылка; `none` — уведомление без ссылки. */
    val subject: NotificationSubjectSchema,
    val isRead: Boolean,
    val createdAt: Instant,
)

/** Объект, к которому относится уведомление (дискриминатор `type`); адрес ссылки строит клиент. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface NotificationSubjectSchema

/** Уведомление ни к какому объекту не относится — ссылки нет. */
@Serializable
@SerialName("none")
data object NoneNotificationSubjectSchema : NotificationSubjectSchema

/** Уведомление относится к группе [id]. */
@Serializable
@SerialName("group")
data class GroupNotificationSubjectSchema(
    val id: GroupId,
) : NotificationSubjectSchema

/** Запрос на отметку конкретных уведомлений прочитанными. */
@Serializable
data class MarkNotificationsReadRequest(
    val ids: List<Uuid>,
)
