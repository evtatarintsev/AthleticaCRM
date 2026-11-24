package org.athletica.crm.domain.mail

import arrow.core.raise.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toOrgEmailId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.time.Duration

/** Очередь исходящих писем в таблице `org_emails`. */
class DbOrgEmails : OrgEmails {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun schedule(
        to: List<EmailAddress>,
        subject: String,
        textBody: String,
        htmlBody: String,
    ) {
        tr.sql(
            """
            INSERT INTO org_emails (id, org_id, to_addresses, subject, text_body, html_body)
            VALUES (:id, :orgId, :to, :subject, :textBody, :htmlBody)
            """.trimIndent(),
        )
            .bind("id", OrgEmailId.new())
            .bind("orgId", ctx.orgId)
            .bind("to", to.map { it.value }.toTypedArray())
            .bind("subject", subject)
            .bind("textBody", textBody)
            .bind("htmlBody", htmlBody)
            .execute()
    }

    /**
     * Захватывает до [limit] писем, готовых к отправке, на время [lease]: сдвигает им
     * `next_attempt_at`, поэтому другие реплики не возьмут их, пока аренда не истечёт.
     * `SKIP LOCKED` не даёт двум конкурентным захватам выбрать одни и те же строки.
     * Если процесс упадёт до отметки результата, письмо снова станет доступно после [lease].
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun claimPending(limit: Int, lease: Duration): List<DbOrgEmail> =
        tr.sql(
            """
            UPDATE org_emails
            SET next_attempt_at = now() + make_interval(secs => :leaseSeconds)
            WHERE id IN (
                SELECT id FROM org_emails
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, org_id, to_addresses, subject, text_body, html_body, attempt_count, created_at
            """.trimIndent(),
        )
            .bind("limit", limit)
            .bind("leaseSeconds", lease.inWholeSeconds.toDouble())
            .list { it.toPendingEmail() }
            .sortedBy { it.createdAt }

    private fun Row.toPendingEmail(): DbOrgEmail {
        @Suppress("UNCHECKED_CAST")
        val addresses = get("to_addresses", Array<String>::class.java) as Array<String>
        return DbOrgEmail(
            id = asUuid("id").toOrgEmailId(),
            orgId = OrgId(asUuid("org_id")),
            to = addresses.map { EmailAddress(it) },
            subject = asString("subject"),
            textBody = asString("text_body"),
            htmlBody = asString("html_body"),
            state = OrgEmailState.Pending,
            attemptCount = asInt("attempt_count"),
            createdAt = asInstant("created_at"),
        )
    }
}
