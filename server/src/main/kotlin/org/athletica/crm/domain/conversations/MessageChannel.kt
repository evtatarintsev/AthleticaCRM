package org.athletica.crm.domain.conversations

import arrow.core.Either
import org.athletica.crm.core.messaging.ChannelType

/**
 * Порт отправки сообщения через конкретный канал связи.
 *
 * Реализации (адаптеры провайдеров) живут вне домена, в слое `integrations`.
 * Домен знает только этот интерфейс — это позволяет добавлять провайдеров,
 * не затрагивая бизнес-логику (гексагональная архитектура).
 */
interface MessageChannel {
    /** Тип канала, который обслуживает этот адаптер. */
    val type: ChannelType

    /**
     * Отправляет [message] провайдеру.
     * Возвращает идентификатор сообщения у провайдера при успехе либо [SendError] при сбое.
     */
    suspend fun send(message: OutboundMessage): Either<SendError, ProviderMessageRef>
}

/**
 * Данные для отправки исходящего сообщения.
 *
 * [recipientAddress] — адрес получателя в терминах канала (телефон, chat_id, email); `null` для IN_APP.
 * [config] — настройки интеграции, через которую идёт отправка.
 */
data class OutboundMessage(
    val recipientAddress: String?,
    val body: String,
    val config: Map<String, String>,
)

/** Идентификатор отправленного сообщения на стороне провайдера (для матчинга квитанций о доставке). */
data class ProviderMessageRef(val value: String)

/**
 * Ошибка отправки сообщения.
 *
 * [Transient] — временный сбой (провайдер недоступен, таймаут, rate-limit): сообщение можно повторить.
 * [Permanent] — окончательный отказ (невалидный адрес, клиент заблокировал бот, нет согласия):
 * повторять бессмысленно, сообщение сразу помечается FAILED.
 */
sealed interface SendError {
    val code: String
    val message: String

    /** Временная ошибка — отправку имеет смысл повторить. */
    data class Transient(override val code: String, override val message: String) : SendError

    /** Постоянная ошибка — повтор бессмысленен. */
    data class Permanent(override val code: String, override val message: String) : SendError
}
