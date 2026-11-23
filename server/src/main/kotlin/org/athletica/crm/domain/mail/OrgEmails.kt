package org.athletica.crm.domain.mail

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Очередь исходящих писем организации. */
interface OrgEmails {
    /** Ставит письмо [subject] на адреса [to] в очередь отправки от имени организации из контекста. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun schedule(
        to: List<EmailAddress>,
        subject: String,
        textBody: String,
        htmlBody: String,
    )
}
