package org.athletica.crm.domain.notifications

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.NotificationId
import org.athletica.crm.core.entityids.toNotificationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [Notifications] поверх таблиц `notifications` и `notification_recipients`. */
class DbNotifications : Notifications {
    override fun new(title: String, body: String, recipients: List<EmployeeId>): Notification = DbNotification(NotificationId.new(), title, body, recipients = recipients)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun of(isRead: Boolean?): List<Notification> {
        val isReadFilter = if (isRead != null) "AND nr.is_read = :isRead" else ""

        return tr
            .sql(
                """
                SELECT n.id, n.title, n.body, nr.is_read, n.created_at
                FROM notifications n
                JOIN notification_recipients nr ON nr.notification_id = n.id
                WHERE nr.employee_id = :employeeId AND n.org_id = :orgId $isReadFilter
                ORDER BY n.created_at DESC
                LIMIT 50
                """.trimIndent(),
            )
            .bind("employeeId", ctx.employeeId)
            .bind("orgId", ctx.orgId)
            .bind("isRead", isRead)
            .list { row ->
                DbNotification(
                    id = row.asUuid("id").toNotificationId(),
                    title = row.asString("title"),
                    body = row.asString("body"),
                    isRead = row.asBoolean("is_read"),
                    createdAt = row.asInstant("created_at"),
                )
            }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unreadCount(): Int =
        tr
            .sql(
                """
                SELECT COUNT(*) AS cnt
                FROM notification_recipients nr
                JOIN notifications n ON n.id = nr.notification_id
                WHERE nr.employee_id = :employeeId AND n.org_id = :orgId AND nr.is_read = false
                """.trimIndent(),
            )
            .bind("employeeId", ctx.employeeId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asLong("cnt") }
            ?.toInt() ?: 0

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markAsRead(ids: List<NotificationId>) {
        if (ids.isEmpty()) {
            return
        }

        tr
            .sql(
                """
                UPDATE notification_recipients nr
                SET is_read = true, read_at = now()
                WHERE nr.notification_id = ANY(:ids)
                  AND nr.employee_id = :employeeId
                  AND EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.id = nr.notification_id AND n.org_id = :orgId
                  )
                """.trimIndent(),
            )
            .bind("ids", ids)
            .bind("employeeId", ctx.employeeId)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markAllAsRead() {
        tr
            .sql(
                """
                UPDATE notification_recipients nr
                SET is_read = true, read_at = now()
                WHERE nr.employee_id = :employeeId
                  AND nr.is_read = false
                  AND EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.id = nr.notification_id AND n.org_id = :orgId
                  )
                """.trimIndent(),
            )
            .bind("employeeId", ctx.employeeId)
            .bind("orgId", ctx.orgId)
            .execute()
    }
}
