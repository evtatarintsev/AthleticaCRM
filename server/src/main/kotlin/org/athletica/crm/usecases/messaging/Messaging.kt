package org.athletica.crm.usecases.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.api.schemas.messaging.SendMessageRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.conversations.Author
import org.athletica.crm.domain.conversations.Conversations
import org.athletica.crm.domain.messagedelivery.Deliveries
import org.athletica.crm.storage.Transaction

/**
 * Отправляет сообщение клиенту через выбранную интеграцию канала.
 *
 * Валидация до создания доставки: интеграция существует, принадлежит организации и включена;
 * адрес получателя разрешается самой интеграцией (прямые каналы — из контакта, связанные — из связки).
 * При нарушении ошибка возвращается до создания сообщения. Создаёт одну доставку (структура поддерживает
 * фан-аут на несколько каналов).
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
    ensure(integration.enabled) {
        CommonDomainError("CHANNEL_DISABLED", "Канал отключён")
    }
    ensure(request.body.isNotBlank()) {
        CommonDomainError("MESSAGE_EMPTY", "Сообщение не может быть пустым")
    }

    val recipientAddress = integration.recipientAddress(request.clientId, request.contactId)

    val conversation = conversations.forClient(request.clientId)
    val message = conversation.appendOutbound(Author.Employee(ctx.employee.id), request.body)
    deliveries.create(message.id, integration.id, recipientAddress)
    conversation.touch()
}
