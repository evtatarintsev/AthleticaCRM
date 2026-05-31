package org.athletica.crm.domain.conversations

import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction

/**
 * Исходящее сообщение, ожидающее отправки, в представлении для фонового диспетчера.
 * Методы worker-пути не привязаны к [EmployeeRequestContext]: диспетчер работает кросс-организационно.
 */
data class PendingMessage(
    val id: MessageId,
    val channelType: ChannelType,
    val channelIntegrationId: ChannelIntegrationId?,
    val recipientAddress: String?,
    val body: String,
    val retryCount: Int,
)

/** Репозиторий сообщений. Запросный путь изолирован по организации, worker-путь — кросс-организационный. */
interface Messages {
    /** Помечает сообщение отправленным (`SENT`) с идентификатором у провайдера. */
    context(tr: Transaction)
    suspend fun markSent(id: MessageId, ref: ProviderMessageRef)

    /** Помечает сообщение окончательно неотправленным (`FAILED`). */
    context(tr: Transaction)
    suspend fun markFailed(id: MessageId, code: String, message: String)

    /** Откладывает повтор: увеличивает счётчик попыток, сообщение остаётся в `QUEUED`. */
    context(tr: Transaction)
    suspend fun retryLater(id: MessageId, error: String)
}
