package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.core.messaging.MessageDirection
import org.athletica.crm.core.messaging.MessageStatus
import org.athletica.crm.core.messaging.SenderKind

/** Сообщение в ленте диалога. */
@Serializable
data class MessageSchema(
    val id: MessageId,
    val channelType: ChannelType,
    val direction: MessageDirection,
    val senderKind: SenderKind,
    val senderEmployeeId: EmployeeId?,
    val body: String,
    val status: MessageStatus,
    val errorMessage: String?,
    /** Момент создания в формате ISO-8601 (UTC). */
    val createdAt: String,
)

/** Лента диалога с клиентом: сообщения по всем каналам, от старых к новым. */
@Serializable
data class ConversationResponse(
    val clientId: ClientId,
    val messages: List<MessageSchema>,
)

/** Запрос ленты диалога с клиентом. */
@Serializable
data class ConversationRequest(
    val clientId: ClientId,
)

/** Запрос на отправку сообщения клиенту через выбранную интеграцию канала. */
@Serializable
data class SendMessageRequest(
    val clientId: ClientId,
    val channelIntegrationId: ChannelIntegrationId,
    val body: String,
)
