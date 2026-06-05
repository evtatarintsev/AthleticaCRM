package org.athletica.crm.domain.messagedelivery

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.errors.DomainError
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
)

/**
 * Репозиторий доставок. Запросный путь (`create`/`byMessage`) изолирован по организации,
 * worker-путь (`pending`/`mark*`) — кросс-организационный.
 */
interface Deliveries {
    /** Создаёт доставку сообщения [messageId] в интеграцию [channelIntegrationId] в статусе `PENDING`. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(
        messageId: MessageId,
        channelIntegrationId: ChannelIntegrationId,
        recipientAddress: String,
    ): Delivery

    /** Возвращает доставки сообщения [messageId] в порядке создания. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byMessage(messageId: MessageId): List<Delivery>

    /**
     * Worker-путь: до [limit] доставок в статусе `PENDING`, чьи интеграции включены
     * (`enabled = true`). Отключённые интеграции исключаются — их доставки остаются `PENDING`.
     */
    context(tr: Transaction)
    suspend fun pending(limit: Int): List<PendingDelivery>

    /** Помечает доставку отправленной (`SENT`) с идентификатором у провайдера. */
    context(tr: Transaction)
    suspend fun markSent(id: DeliveryId, ref: ProviderMessageRef)

    /** Помечает доставку окончательно неуспешной (`FAILED`). */
    context(tr: Transaction)
    suspend fun markFailed(id: DeliveryId, error: DeliveryError)

    /** Откладывает повтор: увеличивает счётчик попыток, доставка остаётся в `PENDING`. */
    context(tr: Transaction)
    suspend fun retryLater(id: DeliveryId, error: DeliveryError)
}
