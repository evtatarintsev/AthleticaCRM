package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.toOrgEmailId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

class DbOrgEmails : OrgEmails {
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun schedule(
        orgId: OrgId,
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

    context(tr: Transaction)
    override suspend fun pending(limit: Int): List<DbOrgEmail> =
        tr.sql(
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
                DbOrgEmail(
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
}
