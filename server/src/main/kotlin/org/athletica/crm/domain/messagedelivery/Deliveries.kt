package org.athletica.crm.domain.messagedelivery

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.storage.Transaction

/**
 * Исходящая доставка, ожидающая отправки, в представлении для фонового диспетчера.
 * Worker-путь не привязан к [EmployeeRequestContext]: диспетчер работает кросс-организационно.
 * [body] подтягивается из сообщения, [recipientAddress] — из самой доставки.
 */
data class PendingDelivery(
    val id: DeliveryId,
    val channelIntegrationId: ChannelIntegrationId,
    val recipientAddress: String,
    val body: String,
    val attempts: Int,
) {
    /**
     * Следующий переход по результату отправки [result]: успех — в `SENT`, постоянная ошибка — в `FAILED`,
     * временная — повтор, пока попытка не [maxRetries]-я по счёту, после неё — `FAILED`.
     */
    fun next(result: SendResult, maxRetries: Int): DeliveryTransition =
        when (result) {
            is SendResult.Sent -> DeliveryTransition.MarkSent(ProviderMessageRef(result.providerRef))
            is SendResult.PermanentFailure -> DeliveryTransition.Fail(DeliveryError(result.code, result.message))
            is SendResult.TransientFailure ->
                if (attempts + 1 >= maxRetries) {
                    DeliveryTransition.Fail(DeliveryError(result.code, result.message))
                } else {
                    DeliveryTransition.Retry(DeliveryError(result.code, result.message))
                }
        }

    /** Переход, когда интеграции-отправителя больше нет: отправить некуда, повтор не поможет. */
    fun withoutSender(): DeliveryTransition = DeliveryTransition.Fail(DeliveryError(CHANNEL_NOT_FOUND, "Интеграция канала удалена"))

    /** Коды ошибок доставки, которые выставляет сам домен, а не провайдер. */
    companion object {
        /** Интеграция канала, через которую шла доставка, не найдена. */
        const val CHANNEL_NOT_FOUND = "CHANNEL_NOT_FOUND"
    }
}

/** Переход доставки из `PENDING`, выбранный [PendingDelivery.next]; диспетчер только исполняет его. */
sealed interface DeliveryTransition {
    /** В `SENT` с идентификатором у провайдера [ref]. */
    data class MarkSent(val ref: ProviderMessageRef) : DeliveryTransition

    /** Остаться в `PENDING` и увеличить счётчик попыток, запомнив последнюю ошибку [error]. */
    data class Retry(val error: DeliveryError) : DeliveryTransition

    /** В терминальный `FAILED` с ошибкой [error]. */
    data class Fail(val error: DeliveryError) : DeliveryTransition
}

/**
 * Репозиторий доставок. Запросный путь (`create`/`byMessage`) изолирован по организации,
 * worker-путь (`pending`/`mark*`) — кросс-организационный.
 */
interface Deliveries {
    /** Создаёт доставку сообщения [messageId] во включённую интеграцию [channelIntegrationId] в статусе `PENDING`. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun create(
        messageId: MessageId,
        channelIntegrationId: ChannelIntegrationId,
        resolveRecipientAddress: suspend () -> String,
    ): Delivery

    /** Возвращает доставки сообщения [messageId] в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byMessage(messageId: MessageId): List<Delivery>

    /**
     * Worker-путь: идентификаторы до [limit] доставок в статусе `PENDING`, чьи интеграции включены
     * (`enabled = true`). Отключённые интеграции исключаются — их доставки остаются `PENDING`.
     * Снимок без блокировок: перед отправкой доставку нужно захватить через [claim].
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun pending(limit: Int): List<DeliveryId>

    /**
     * Захватывает доставку [id] до конца транзакции (`FOR UPDATE SKIP LOCKED`), если она всё ещё `PENDING`
     * и её интеграция включена. `null` — доставку уже обработали или её держит другой обработчик.
     */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun claim(id: DeliveryId): PendingDelivery?

    /** Переводит доставку из `PENDING` в `SENT` с идентификатором у провайдера. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun markSent(id: DeliveryId, ref: ProviderMessageRef)

    /** Переводит доставку из `PENDING` в терминальный `FAILED`. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun markFailed(id: DeliveryId, error: DeliveryError)

    /** Откладывает повтор доставки в `PENDING`: увеличивает счётчик попыток. */
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun retryLater(id: DeliveryId, error: DeliveryError)
}
