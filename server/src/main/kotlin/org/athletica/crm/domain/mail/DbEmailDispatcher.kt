package org.athletica.crm.domain.mail

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.storage.Database
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Subject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface EmailDispatcher {
    suspend fun dispatchPending()
}

/**
 * Фоновый диспетчер: читает PENDING-письма из [OrgEmails] и отправляет через [smtp].
 * Запускается периодически воркером (например, каждые 30 секунд).
 * После успешной отправки помечает письмо как SENT, при ошибке — FAILED.
 */
class DbEmailDispatcher(
    private val db: Database,
    private val emails: DbOrgEmails,
    private val smtp: Mailbox,
    private val checkEvery: Duration = 10.seconds,
) : EmailDispatcher {
    override suspend fun dispatchPending() {
        while (currentCoroutineContext().isActive) {
            val pendingEmails = db.transaction { emails.pending() }
            pendingEmails.forEach { email ->
                // TODO: Убрать runCatching когда smtp.send перестанет бросать исключения
                runCatching { smtp.send(email.toSmtpEmail()) }
                    .onFailure { e -> if (e is CancellationException) throw e }
                    .onSuccess { db.transaction { email.markSent(email.id) } }
                    .onFailure { e -> db.transaction { email.markFailed(email.id, e.message ?: "unknown error") } }
            }
            delay(checkEvery)
        }
    }

    private fun DbOrgEmail.toSmtpEmail() =
        Email(
            subject = Subject(subject),
            text = EmailTextBody(textBody),
            html = EmailHtmlBody(htmlBody),
            to = to,
        )
}
