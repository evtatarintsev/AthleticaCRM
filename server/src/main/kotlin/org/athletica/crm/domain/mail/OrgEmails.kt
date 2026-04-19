package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.storage.Transaction

interface OrgEmails {
    context(ctx: RequestContext, tr: Transaction)
    suspend fun schedule(
        orgId: OrgId,
        to: List<EmailAddress>,
        subject: String,
        textBody: String,
        htmlBody: String,
    )

    /** Возвращает очередь писем ожидающих отправки. */
    context(tr: Transaction)
    suspend fun pending(limit: Int = 50): List<OrgEmail>
}
