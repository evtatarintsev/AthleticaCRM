package org.athletica.crm.domain.channels

/**
 * Исход попытки отправки сообщения провайдеру — транспортный результат канала.
 *
 * [TransientFailure] — временный сбой (провайдер недоступен, таймаут, rate-limit): отправку можно
 * повторить. [PermanentFailure] — окончательный отказ (невалидный адрес, клиент заблокировал бот):
 * повторять бессмысленно. Диспетчер по этому различию решает retry vs fail.
 */
sealed interface SendResult {
    /** Успех: [providerRef] — идентификатор сообщения у провайдера (для матчинга квитанций). */
    data class Sent(val providerRef: String) : SendResult

    /** Временная ошибка — отправку имеет смысл повторить. */
    data class TransientFailure(val code: String, val message: String) : SendResult

    /** Постоянная ошибка — повтор бессмысленен. */
    data class PermanentFailure(val code: String, val message: String) : SendResult
}
