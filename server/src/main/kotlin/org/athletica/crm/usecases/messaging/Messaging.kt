package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.conversations.Author
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.storage.Transaction

/**
 * Отправляет сообщение клиенту через выбранную интеграцию канала.
 *
 * Сообщение и доставка создаются в одной транзакции. Интеграция разрешает адрес получателя
 * из контакта или связки после проверки включённости канала.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun sendMessage(
    request: SendMessageRequest,
    channels: ChannelIntegrations,
    conversations: Conversations,
    deliveries: Deliveries,
) {
    // TODO: ограничить право отправки отдельным UserPermission (например, CAN_SEND_MESSAGES),
    // когда появится дефолтная роль, которой это право выдаётся.
    val integration = channels.byId(request.channelIntegrationId)
    val conversation = conversations.forClient(request.clientId)
    val message = conversation.appendOutbound(Author.Employee(ctx.employee.id), request.body)
    deliveries.create(message.id, integration.id) { integration.recipientAddress(request.clientId, request.contactId) }
    conversation.touch()
}
