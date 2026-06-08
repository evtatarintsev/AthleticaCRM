package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.DeliverySchema
import org.athletica.crm.api.schemas.messaging.DeliveryStateSchema
import org.athletica.crm.api.schemas.messaging.InboundMessageSchema
import org.athletica.crm.api.schemas.messaging.MessageSchema
import org.athletica.crm.api.schemas.messaging.OutboundMessageSchema
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.domain.conversations.Author
import org.athletica.crm.domain.conversations.Conversation
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.conversations.InboundMessage
import org.athletica.crm.domain.conversations.Message
import org.athletica.crm.domain.conversations.OutboundMessage
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.domain.messagedelivery.Delivery
import org.athletica.crm.domain.messagedelivery.DeliveryState
import org.athletica.crm.storage.Transaction

/**
 * Отправляет сообщение клиенту через выбранную интеграцию канала.
 *
 * Валидация до создания доставки: интеграция существует, принадлежит организации и включена;
 * у клиента есть контакт нужного типа (кроме IN_APP, где адрес получателя — сам клиент). При
 * нарушении ошибка возвращается до создания сообщения. Создаёт одну доставку (структура поддерживает
 * фан-аут на несколько каналов). Возвращает обновлённую ленту диалога.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun sendMessage(
    request: SendMessageRequest,
    channels: ChannelIntegrations,
    contacts: ClientContacts,
    conversations: Conversations,
    deliveries: Deliveries,
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
            request.clientId.toString()
        } else {
            val candidates =
                contacts
                    .byClient(request.clientId)
                    .filter { integration.channelType in it.type.compatibleChannels }
            val chosen =
                request.contactId
                    ?.let { id ->
                        candidates.firstOrNull { it.id == id }
                            ?: raise(CommonDomainError("CONTACT_NOT_FOUND", "Контакт не найден или не подходит для канала"))
                    }
                    ?: candidates.firstOrNull()
                    ?: raise(CommonDomainError("CLIENT_HAS_NO_CONTACT", "У клиента нет контакта для этого канала"))
            chosen.value
        }

    val conversation = conversations.forClient(request.clientId)
    val message = conversation.appendOutbound(Author.Employee(ctx.employeeId), request.body)
    deliveries.create(message.id, integration.id, recipientAddress)
    conversation.touch()

    return conversationResponse(request.clientId, conversation, deliveries, contacts)
}

/** Возвращает ленту диалога с клиентом [clientId] (сообщения по всем каналам, от старых к новым). */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun conversationView(
    clientId: ClientId,
    conversations: Conversations,
    deliveries: Deliveries,
    contacts: ClientContacts,
): ConversationResponse {
    val conversation = conversations.forClient(clientId)
    return conversationResponse(clientId, conversation, deliveries, contacts)
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun conversationResponse(
    clientId: ClientId,
    conversation: Conversation,
    deliveries: Deliveries,
    contacts: ClientContacts,
): ConversationResponse =
    ConversationResponse(
        clientId = clientId,
        messages = conversation.messages().map { it.toSchema(deliveries) },
        contacts = contacts.byClient(clientId).map { it.toSchema() },
    )

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun Message.toSchema(deliveries: Deliveries): MessageSchema =
    when (this) {
        is OutboundMessage ->
            OutboundMessageSchema(
                id = id,
                body = body,
                createdAt = createdAt.toString(),
                authorEmployeeId = (author as? Author.Employee)?.id,
                deliveries = deliveries.byMessage(id).map { it.toSchema() },
            )

        is InboundMessage ->
            InboundMessageSchema(
                id = id,
                body = body,
                createdAt = createdAt.toString(),
                receivedVia = receivedVia,
            )
    }

private fun ClientContact.toSchema(): ClientContactSchema =
    ClientContactSchema(
        id = id,
        type = type,
        value = value,
    )

private fun Delivery.toSchema(): DeliverySchema =
    DeliverySchema(
        channelIntegrationId = channelIntegrationId,
        state =
            when (state) {
                DeliveryState.Pending -> DeliveryStateSchema.PENDING
                DeliveryState.Sent -> DeliveryStateSchema.SENT
                DeliveryState.Delivered -> DeliveryStateSchema.DELIVERED
                is DeliveryState.Failed -> DeliveryStateSchema.FAILED
            },
        errorMessage = (state as? DeliveryState.Failed)?.error?.message,
    )
