package org.athletica.crm.domain.mail

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.storage.Database
import org.athletica.infra.mail.Email
import org.athletica.infra.mail.EmailHtmlBody
import org.athletica.infra.mail.EmailTextBody
import org.athletica.infra.mail.Subject
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Фоновая отправка исходящих писем организации. */
interface EmailDispatcher {
    /** Запускает бесконечный цикл отправки. Должен выполняться в отдельной корутине. */
    suspend fun dispatchPending()
}

/**
 * Фоновый диспетчер: захватывает ожидающие письма из [emails] и отправляет через [smtp].
 * Успех переводит письмо в `Sent`. Ошибка отправки считается временной: письмо остаётся
 * `Pending` и повторяется через [retryDelay], а на [maxAttempts]-й неудаче переходит в `Failed`.
 * SMTP вызывается вне транзакции: от двойной отправки защищает аренда в [DbOrgEmails.claimPending].
 */
class DbEmailDispatcher(
    private val db: Database,
    private val emails: DbOrgEmails,
    private val smtp: Mailbox,
    private val checkEvery: Duration = 10.seconds,
    private val maxAttempts: Int = 5,
    private val retryDelay: Duration = 1.minutes,
    private val lease: Duration = 10.minutes,
) : EmailDispatcher {
    private val logger = LoggerFactory.getLogger(DbEmailDispatcher::class.java)

    override suspend fun dispatchPending() {
        while (currentCoroutineContext().isActive) {
            try {
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Ошибка в цикле отправки писем: ${e.message}", e)
            }
            delay(checkEvery)
        }
    }

    /** Обрабатывает одну порцию писем из очереди. Выделено для прямого вызова в тестах. */
    suspend fun pollOnce() {
        db.transaction { orThrow { emails.claimPending(20, lease) } }.forEach { dispatch(it) }
    }

    private suspend fun dispatch(email: DbOrgEmail) {
        // TODO: Убрать runCatching когда smtp.send перестанет бросать исключения
        runCatching { smtp.send(email.toSmtpEmail()) }
            .onFailure { e -> if (e is CancellationException) throw e }
            .onSuccess { db.transaction { orThrow { email.markSent() } } }
            .onFailure { e ->
                val error = e.message ?: "unknown error"
                logger.warn("Не удалось отправить письмо ${email.id}, попытка ${email.attemptCount + 1}: $error")
                db.transaction {
                    orThrow {
                        if (email.attemptCount + 1 >= maxAttempts) {
                            email.markFailed(error)
                        } else {
                            email.retryLater(retryDelay)
                        }
                    }
                }
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
