package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.OrgEmailId
import org.athletica.crm.core.OrgId
import kotlin.time.Clock
import kotlin.time.Instant

enum class OrgEmailStatus {
    PENDING,
    SENT,
    FAILED,
}

data class OrgEmail(
    val id: OrgEmailId = OrgEmailId.new(),
    val orgId: OrgId,
    val to: List<EmailAddress>,
    val subject: String,
    val textBody: String,
    val htmlBody: String,
    val status: OrgEmailStatus = OrgEmailStatus.PENDING,
    val attemptCount: Int = 0,
    val createdAt: Instant = Clock.System.now(),
    val sentAt: Instant? = null,
    val lastError: String? = null,
)
