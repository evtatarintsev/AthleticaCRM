package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Диалог с клиентом: агрегирует сообщения по всем каналам. Один на клиента. */
interface Conversation {
    val id: ConversationId

    /**
     * Добавляет исходящее сообщение от [author] с текстом [body]. Только контент —
     * постановка доставок в каналы выполняется отдельно (агрегат `Delivery`). Возвращает сообщение.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun appendOutbound(author: Author, body: String): OutboundMessage

    /** Добавляет входящее сообщение от клиента, полученное по каналу [receivedVia]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun appendInbound(receivedVia: ChannelIntegrationId, body: String): InboundMessage

    /** Обновляет момент последнего сообщения диалога на «сейчас». */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun touch()

    /** Возвращает все сообщения диалога в порядке от старых к новым. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun messages(): List<Message>
}
