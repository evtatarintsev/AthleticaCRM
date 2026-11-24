package org.athletica.crm.domain.mail

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Письмо из таблицы `org_emails`. Переходы состояния меняют только эту строку и только пока она
 * в `PENDING`: повторная отметка уже отправленного или отклонённого письма ничего не делает.
 */
data class DbOrgEmail(
    override val id: OrgEmailId,
    override val orgId: OrgId,
    override val to: List<EmailAddress>,
    override val subject: String,
    override val textBody: String,
    override val htmlBody: String,
    override val state: OrgEmailState,
    override val attemptCount: Int,
    override val createdAt: Instant,
) : OrgEmail {
    /** Отмечает письмо отправленным: `Pending` → `Sent`. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun markSent() {
        tr.sql(
            """
            UPDATE org_emails
            SET status = 'SENT', sent_at = now(), attempt_count = attempt_count + 1
            WHERE id = :id AND status = 'PENDING'
            """.trimIndent(),
        )
            .bind("id", id)
            .execute()
    }

    /** Отмечает письмо окончательно не отправленным с ошибкой [error]: `Pending` → `Failed`. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun markFailed(error: String) {
        tr.sql(
            """
            UPDATE org_emails
            SET status = 'FAILED', last_error = :error, attempt_count = attempt_count + 1
            WHERE id = :id AND status = 'PENDING'
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("error", error)
            .execute()
    }

    /** Оставляет письмо в `Pending` после неудачной попытки и откладывает следующую на [delay]. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun retryLater(delay: Duration) {
        tr.sql(
            """
            UPDATE org_emails
            SET attempt_count = attempt_count + 1, next_attempt_at = now() + make_interval(secs => :delaySeconds)
            WHERE id = :id AND status = 'PENDING'
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("delaySeconds", delay.inWholeSeconds.toDouble())
            .execute()
    }
}
