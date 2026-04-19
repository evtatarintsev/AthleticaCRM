package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import kotlin.time.Instant

interface OrgEmail {
    val id: OrgEmailId
    val orgId: OrgId
    val to: List<EmailAddress>
    val subject: String
    val textBody: String
    val htmlBody: String
    val status: OrgEmailStatus
    val attemptCount: Int
    val createdAt: Instant
    val sentAt: Instant?
    val lastError: String?
}
