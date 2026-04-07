package org.athletica.crm.usecases.notifications

import arrow.core.Either
import arrow.core.right
import kotlinx.datetime.Instant
import org.athletica.crm.api.schemas.notifications.NotificationItem
import org.athletica.crm.api.schemas.notifications.NotificationsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает уведомления текущего пользователя ([ctx]) в рамках его организации.
 *
 * [isRead] — опциональный фильтр: `true` — только прочитанные, `false` — только непрочитанные,
 * `null` — все. [NotificationsResponse.unreadCount] всегда отражает полное число непрочитанных
 * и не зависит от фильтра.
 *
 * Возвращает не более 50 последних уведомлений, отсортированных по убыванию [NotificationItem.createdAt].
 */
context(db: Database, ctx: RequestContext)
suspend fun notificationList(isRead: Boolean?): Either<CommonDomainError, NotificationsResponse> {
    val conditions = mutableListOf("nr.user_id = :userId", "n.org_id = :orgId")
    if (isRead != null) conditions += "nr.is_read = :isRead"
    val whereClause = conditions.joinToString(" AND ")

    val notifications =
        db
            .sql(
                """
                SELECT n.id, n.title, n.body, nr.is_read, n.created_at
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                WHERE $whereClause
                ORDER BY n.created_at DESC
                LIMIT 50
                """.trimIndent(),
            ).bind("userId", ctx.userId.value)
            .bind("orgId", ctx.orgId.value)
            .let { q -> if (isRead != null) q.bind("isRead", isRead) else q }
            .list { row, _ ->
                NotificationItem(
                    id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                    title = row.get("title", String::class.java)!!,
                    body = row.get("body", String::class.java)!!,
                    isRead = row.get("is_read", Boolean::class.java)!!,
                    createdAt = row.get("created_at", java.time.OffsetDateTime::class.java)!!
                        .toInstant()
                        .let { Instant.fromEpochSeconds(it.epochSecond, it.nano.toLong()) },
                )
            }

    val unreadCount =
        db
            .sql(
                """
                SELECT COUNT(*) AS cnt
                FROM notification_recipients nr
                JOIN notifications n ON n.id = nr.notification_id
                WHERE nr.user_id = :userId AND n.org_id = :orgId AND nr.is_read = false
                """.trimIndent(),
            ).bind("userId", ctx.userId.value)
            .bind("orgId", ctx.orgId.value)
            .firstOrNull { row -> row.get("cnt", Long::class.java)!! }
            ?.toInt() ?: 0

    return NotificationsResponse(
        notifications = notifications,
        unreadCount = unreadCount,
    ).right()
}
