package org.athletica.crm.domain.conversations

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.core.messaging.MessageDirection
import org.athletica.crm.core.messaging.MessageStatus
import org.athletica.crm.core.messaging.SenderKind
import kotlin.time.Instant

/** Диалог с клиентом: агрегирует сообщения по всем каналам. Один на клиента. */
data class Conversation(
    val id: ConversationId,
    val clientId: ClientId,
)

/**
 * Сообщение в диалоге.
 *
 * Ссылки на соседние агрегаты — только по идентификатору ([senderEmployeeId], [channelIntegrationId]):
 * человекочитаемое представление собирает слой routes/projection.
 */
data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val channelIntegrationId: ChannelIntegrationId?,
    val channelType: ChannelType,
    val direction: MessageDirection,
    val senderKind: SenderKind,
    val senderEmployeeId: EmployeeId?,
    val recipientAddress: String?,
    val body: String,
    val status: MessageStatus,
    val errorMessage: String?,
    val createdAt: Instant,
)
