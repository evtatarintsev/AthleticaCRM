package org.athletica.crm.domain.mail

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

interface OrgEmails {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun schedule(
        to: List<EmailAddress>,
        subject: String,
        textBody: String,
        htmlBody: String,
    )

    /** Возвращает очередь писем ожидающих отправки. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun pending(limit: Int = 50): List<OrgEmail>
}
