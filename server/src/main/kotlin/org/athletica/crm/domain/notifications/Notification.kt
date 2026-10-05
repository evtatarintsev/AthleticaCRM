package org.athletica.crm.domain.notifications

import arrow.core.raise.context.Raise
import org.athletica.crm.core.Lang
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.NotificationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Уведомление организации. Создаётся через [Notifications.new] и сохраняется вызовом [save];
 * читается через [Notifications.of]. Признак прочитанности относится к текущему сотруднику.
 */
interface Notification {
    /** Идентификатор уведомления. */
    val id: NotificationId

    /** Объект уведомления; `null`, если ссылаться не на что. */
    val subject: NotificationSubject?

    /** Прочитано ли уведомление текущим сотрудником; для только что созданного — `false`. */
    val isRead: Boolean

    /** Момент создания. */
    val createdAt: Instant

    /** Заголовок на языке [lang]. */
    fun title(lang: Lang): String

    /** Текст на языке [lang]. */
    fun body(lang: Lang): String

    /**
     * Сохраняет уведомление и назначает его получателям в рамках [ctx].orgId.
     * Если список получателей пуст — ничего не сохраняет.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()
}
