package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.errors.DomainError
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
    /**
     * Ставит исходящее сообщение в очередь (`QUEUED`) от лица текущего сотрудника (`ADMIN`).
     * [channelIntegrationId] — интеграция, через которую пойдёт отправка; [recipientAddress] —
     * адрес получателя (`null` для IN_APP). Возвращает созданное сообщение.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun enqueue(
        conversationId: ConversationId,
        channelIntegrationId: ChannelIntegrationId?,
        channelType: ChannelType,
        recipientAddress: String?,
        body: String,
    ): Message

    /** Возвращает все сообщения диалога [conversationId] в порядке от старых к новым. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byConversation(conversationId: ConversationId): List<Message>

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
