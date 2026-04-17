package org.athletica.crm.domain.mail

import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailAddress
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Mailbox
import org.athletica.infra.mail.Subject

/**
 * Фоновый диспетчер: читает PENDING-письма из [OrgEmails] и отправляет через [smtp].
 * Запускается периодически воркером (например, каждые 30 секунд).
 * После успешной отправки помечает письмо как SENT, при ошибке — FAILED.
 */
class EmailDispatcher(
    private val emails: OrgEmails,
    private val smtp: Mailbox,
) {
    suspend fun dispatchPending() {
        emails.pending().forEach { email ->
            runCatching { smtp.send(email.toSmtpEmail()) }
                .onSuccess { emails.markSent(email.id) }
                .onFailure { e -> emails.markFailed(email.id, e.message ?: "unknown error") }
        }
    }
}

private fun OrgEmail.toSmtpEmail() =
    Email(
        subject = Subject(subject),
        text = EmailTextBody(textBody),
        html = EmailHtmlBody(htmlBody),
        to = to.map { EmailAddress(it) },
    )
