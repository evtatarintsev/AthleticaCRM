package org.athletica.crm.domain.notifications

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.NotificationId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Реализация [Notification] поверх таблиц `notifications` и `notification_recipients`.
 *
 * [recipients] задаётся только при создании через [Notifications.new] и используется в [save];
 * при чтении из БД он пуст.
 */
data class DbNotification(
    override val id: NotificationId,
    override val title: String,
    override val body: String,
    private val recipients: List<EmployeeId> = emptyList(),
    override val isRead: Boolean = false,
    override val createdAt: Instant = Clock.System.now(),
) : Notification {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        ensure(title.isNotBlank()) {
            CommonDomainError("NOTIFICATION_TITLE_REQUIRED", "Заголовок уведомления обязателен")
        }
        ensure(title.length <= 255) {
            CommonDomainError("NOTIFICATION_TITLE_TOO_LONG", "Заголовок уведомления не должен превышать 255 символов")
        }
        ensure(body.isNotBlank()) {
            CommonDomainError("NOTIFICATION_BODY_REQUIRED", "Текст уведомления обязателен")
        }
        if (recipients.isEmpty()) {
            return
        }

        tr
            .sql("INSERT INTO notifications (id, org_id, title, body) VALUES (:id, :orgId, :title, :body)")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("title", title)
            .bind("body", body)
            .execute()

        tr
            .sql(
                """
                INSERT INTO notification_recipients (notification_id, employee_id)
                SELECT :id, e FROM unnest(:employeeIds) AS e
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("employeeIds", recipients)
            .execute()
    }
}
