package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.MessageSchema
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.conversations.Conversation
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.conversations.Message
import org.athletica.crm.storage.Transaction

/**
 * Ставит сообщение в очередь на отправку клиенту через выбранную интеграцию канала.
 *
 * Валидация до постановки в outbox: интеграция существует, принадлежит организации и включена;
 * у клиента есть контакт нужного типа (кроме IN_APP, где адрес не требуется). При нарушении —
 * ошибка возвращается до создания сообщения. Возвращает обновлённую ленту диалога.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun sendMessage(
    request: SendMessageRequest,
    channels: ChannelIntegrations,
    contacts: ClientContacts,
    conversations: Conversations,
): ConversationResponse {
    // TODO: ограничить право отправки отдельным UserPermission (например, CAN_SEND_MESSAGES),
    // когда появится дефолтная роль, которой это право выдаётся.
    val integration = channels.byId(request.channelIntegrationId)
    ensure(integration.enabled) {
        CommonDomainError("CHANNEL_DISABLED", "Канал отключён")
    }
    ensure(request.body.isNotBlank()) {
        CommonDomainError("MESSAGE_EMPTY", "Сообщение не может быть пустым")
    }

    val recipientAddress =
        if (integration.channelType == ChannelType.IN_APP) {
            null
        } else {
            contacts.addressFor(request.clientId, integration.channelType)
                ?: raise(CommonDomainError("CLIENT_HAS_NO_CONTACT", "У клиента нет контакта для этого канала"))
        }

    val conversation = conversations.forClient(request.clientId)
    conversation.enqueue(
        channelIntegrationId = integration.id,
        channelType = integration.channelType,
        recipientAddress = recipientAddress,
        body = request.body,
    )
    conversation.touch()

    return conversationResponse(request.clientId, conversation)
}

/** Возвращает ленту диалога с клиентом [clientId] (сообщения по всем каналам, от старых к новым). */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun conversationView(
    clientId: ClientId,
    conversations: Conversations,
): ConversationResponse {
    val conversation = conversations.forClient(clientId)
    return conversationResponse(clientId, conversation)
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun conversationResponse(
    clientId: ClientId,
    conversation: Conversation,
): ConversationResponse =
    ConversationResponse(
        clientId = clientId,
        messages = conversation.messages().map { it.toSchema() },
    )

private fun Message.toSchema(): MessageSchema =
    MessageSchema(
        id = id,
        channelType = channelType,
        direction = direction,
        senderKind = senderKind,
        senderEmployeeId = senderEmployeeId,
        body = body,
        status = status,
        errorMessage = errorMessage,
        createdAt = createdAt.toString(),
    )
