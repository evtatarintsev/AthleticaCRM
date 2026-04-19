package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

enum class OrgEmailStatus {
    PENDING,
    SENT,
    FAILED,
}

data class DbOrgEmail(
    override val id: OrgEmailId = OrgEmailId.new(),
    override val orgId: OrgId,
    override val to: List<EmailAddress>,
    override val subject: String,
    override val textBody: String,
    override val htmlBody: String,
    override val status: OrgEmailStatus = OrgEmailStatus.PENDING,
    override val attemptCount: Int = 0,
    override val createdAt: Instant = Clock.System.now(),
    override val sentAt: Instant? = null,
    override val lastError: String? = null,
) : OrgEmail {
    context(tr: Transaction)
    suspend fun markSent(id: OrgEmailId) {
        tr.sql(
            """
            UPDATE org_emails
            SET status = 'SENT', sent_at = now(), attempt_count = attempt_count + 1
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .execute()
    }

    context(tr: Transaction)
    suspend fun markFailed(id: OrgEmailId, error: String) {
        tr.sql(
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
