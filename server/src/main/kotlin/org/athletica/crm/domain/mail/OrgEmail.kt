package org.athletica.crm.domain.mail

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.OrgEmailId
import org.athletica.crm.core.entityids.OrgId
import kotlin.time.Instant

/** Исходящее письмо организации, поставленное в очередь отправки. */
interface OrgEmail {
    /** Идентификатор письма. */
    val id: OrgEmailId

    /** Организация, от имени которой отправляется письмо. */
    val orgId: OrgId

    /** Адреса получателей. */
    val to: List<EmailAddress>

    /** Тема письма. */
    val subject: String

    /** Текстовая версия тела письма. */
    val textBody: String

    /** HTML-версия тела письма. */
    val htmlBody: String

    /** Текущее состояние отправки. */
    val state: OrgEmailState

    /** Число уже сделанных попыток отправки. */
    val attemptCount: Int

    /** Момент постановки письма в очередь. */
    val createdAt: Instant
}

/**
 * Состояние исходящего письма. Сумма-тип: время отправки представимо только в [Sent],
 * ошибка — только в [Failed]. Переходы возможны только из [Pending].
 */
sealed interface OrgEmailState {
    /** Ожидает отправки (в том числе повторной после временной ошибки). */
    data object Pending : OrgEmailState

    /** Отправлено в SMTP. */
    data class Sent(
        /** Момент успешной отправки. */
        val at: Instant,
    ) : OrgEmailState

    /** Терминальный отказ: попытки отправки исчерпаны. */
    data class Failed(
        /** Текст последней ошибки отправки. */
        val error: String,
    ) : OrgEmailState
}
