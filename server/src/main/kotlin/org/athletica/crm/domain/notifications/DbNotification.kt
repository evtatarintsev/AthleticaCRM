package org.athletica.crm.domain.notifications

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.NotificationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Реализация [Notification] поверх таблиц `notifications` и `notification_recipients`.
 * Заголовок, текст и объект берутся из [content].
 *
 * [recipients] задаётся только при создании через [Notifications.new] и используется в [save];
 * при чтении из БД он пуст.
 */
data class DbNotification(
    override val id: NotificationId,
    private val content: NotificationContent,
    private val recipients: List<EmployeeId> = emptyList(),
    override val isRead: Boolean = false,
    override val createdAt: Instant = Clock.System.now(),
) : Notification {
    override val subject: NotificationSubject?
        get() = content.subject

    override fun title(lang: Lang): String = content.title(lang)

    override fun body(lang: Lang): String = content.body(lang)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        if (recipients.isEmpty()) {
            return
        }

        tr
            .sql("INSERT INTO notifications (id, org_id, content) VALUES (:id, :orgId, :content::jsonb)")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("content", Json.encodeToString(content))
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
