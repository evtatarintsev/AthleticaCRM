package org.athletica.crm.usecases.notifications

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.notifications.NotificationItem
import org.athletica.crm.api.schemas.notifications.NotificationsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asBoolean
import org.athletica.crm.db.asInstant
import org.athletica.crm.db.asLong
import org.athletica.crm.db.asString
import org.athletica.crm.db.asUuid

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
    val isReadFilter = if (isRead != null) "AND nr.is_read = :isRead" else ""

    val notifications =
        db
            .sql(
                """
                SELECT n.id, n.title, n.body, nr.is_read, n.created_at
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                WHERE nr.user_id = :userId AND n.org_id = :orgId $isReadFilter
                ORDER BY n.created_at DESC
                LIMIT 50
                """.trimIndent(),
            ).bind("userId", ctx.userId)
            .bind("orgId", ctx.orgId)
            .bind("isRead", isRead)
            .list { row ->
                NotificationItem(
                    id = row.asUuid("id"),
                    title = row.asString("title"),
                    body = row.asString("body"),
                    isRead = row.asBoolean("is_read"),
                    createdAt = row.asInstant("created_at"),
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
            ).bind("userId", ctx.userId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asLong("cnt") }
            ?.toInt() ?: 0

    return NotificationsResponse(
        notifications = notifications,
        unreadCount = unreadCount,
    ).right()
}
