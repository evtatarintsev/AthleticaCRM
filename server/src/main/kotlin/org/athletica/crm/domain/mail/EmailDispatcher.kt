package org.athletica.crm.domain.mail

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Mailbox
import org.athletica.infra.mail.Subject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Фоновый диспетчер: читает PENDING-письма из [OrgEmails] и отправляет через [smtp].
 * Запускается периодически воркером (например, каждые 30 секунд).
 * После успешной отправки помечает письмо как SENT, при ошибке — FAILED.
 */
class EmailDispatcher(
    private val emails: OrgEmails,
    private val smtp: Mailbox,
    private val checkEvery: Duration = 10.seconds,
) {
    suspend fun dispatchPending() {
        while (currentCoroutineContext().isActive) {
            emails.pending().forEach { email ->
                // TODO: Убрать runCatching когда smtp.send перестанет бросать исключения
                runCatching { smtp.send(email.toSmtpEmail()) }
                    .onFailure { e -> if (e is CancellationException) throw e }
                    .onSuccess { emails.markSent(email.id) }
                    .onFailure { e -> emails.markFailed(email.id, e.message ?: "unknown error") }
            }
            delay(checkEvery)
        }
    }
}

private fun OrgEmail.toSmtpEmail() =
    Email(
        subject = Subject(subject),
        text = EmailTextBody(textBody),
        html = EmailHtmlBody(htmlBody),
        to = to,
    )
