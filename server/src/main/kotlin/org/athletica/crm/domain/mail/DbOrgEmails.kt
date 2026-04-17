package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.OrgEmailId
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.toOrgEmailId
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

class DbOrgEmails(private val db: Database) : OrgEmails {
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun schedule(email: OrgEmail) {
        tr.sql(
            """
            INSERT INTO org_emails (id, org_id, to_addresses, subject, text_body, html_body)
            VALUES (:id, :orgId, :to, :subject, :textBody, :htmlBody)
            """.trimIndent(),
        )
            .bind("id", email.id)
            .bind("orgId", ctx.orgId)
            .bind("to", email.to.map { it.value }.toTypedArray())
            .bind("subject", email.subject)
            .bind("textBody", email.textBody)
            .bind("htmlBody", email.htmlBody)
            .execute()
    }

    override suspend fun pending(limit: Int): List<OrgEmail> =
        db.sql(
            """
            SELECT id, org_id, to_addresses, subject, text_body, html_body,
                   status, attempt_count, created_at, sent_at, last_error
            FROM org_emails
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            """.trimIndent(),
        )
            .bind("limit", limit)
            .list { row ->
                @Suppress("UNCHECKED_CAST")
                val toArr = row.get("to_addresses", Array<String>::class.java) as Array<String>
                OrgEmail(
                    id = row.asUuid("id").toOrgEmailId(),
                    orgId = OrgId(row.asUuid("org_id")),
                    to = toArr.map { EmailAddress(it) },
                    subject = row.asString("subject"),
                    textBody = row.asString("text_body"),
                    htmlBody = row.asString("html_body"),
                    status = OrgEmailStatus.valueOf(row.asString("status")),
                    attemptCount = row.asInt("attempt_count"),
                    createdAt = row.asInstant("created_at"),
                    sentAt = null,
                    lastError = row.asStringOrNull("last_error"),
                )
            }

    override suspend fun markSent(id: OrgEmailId) {
        db.sql(
            """
            UPDATE org_emails
            SET status = 'SENT', sent_at = now(), attempt_count = attempt_count + 1
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .execute()
    }

    override suspend fun markFailed(id: OrgEmailId, error: String) {
        db.sql(
            """
            UPDATE org_emails
            SET status = 'FAILED', last_error = :error, attempt_count = attempt_count + 1
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("error", error)
            .execute()
    }
}
