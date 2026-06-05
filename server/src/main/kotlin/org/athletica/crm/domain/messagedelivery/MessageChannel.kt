package org.athletica.crm.domain.messagedelivery

import arrow.core.Either

/**
 * Порт отправки сообщения через конкретный канал связи.
 *
 * Адаптер уже сконфигурирован (создаётся реестром по конфигу интеграции), поэтому в [send]
 * передаётся только адрес получателя и текст. Реализации (адаптеры провайдеров) живут вне домена,
 * в слое `integrations` — это позволяет добавлять провайдеров, не затрагивая бизнес-логику.
 */
interface MessageChannel {
    /**
     * Отправляет сообщение [request] провайдеру.
     * Возвращает идентификатор сообщения у провайдера при успехе либо [SendError] при сбое.
     */
    suspend fun send(request: ChannelSendRequest): Either<SendError, ProviderMessageRef>
}

/**
 * Данные для отправки исходящего сообщения.
 * [recipientAddress] — адрес получателя в адресном пространстве канала; для IN_APP — идентификатор
 * клиента строкой. Конфиг не дублируется: он уже внутри сконфигурированного адаптера.
 */
data class ChannelSendRequest(
    val recipientAddress: String,
    val body: String,
)

/**
 * Ошибка отправки сообщения — транспортный тип порта.
 *
 * [Transient] — временный сбой (провайдер недоступен, таймаут, rate-limit): отправку можно повторить.
 * [Permanent] — окончательный отказ (невалидный адрес, клиент заблокировал бот, нет согласия):
 * повторять бессмысленно. Диспетчер по этому различию решает retry vs fail и при терминальном
 * исходе конвертирует ошибку в [DeliveryError].
 */
sealed interface SendError {
    val code: String
    val message: String

    /** Временная ошибка — отправку имеет смысл повторить. */
    data class Transient(override val code: String, override val message: String) : SendError

    /** Постоянная ошибка — повтор бессмысленен. */
    data class Permanent(override val code: String, override val message: String) : SendError
}
