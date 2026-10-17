package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.InboundReceiptId

/** Запрос сообщений неразобранного чата [address] интеграции [integrationId]. */
@Serializable
data class TriageChatRequest(
    val integrationId: ChannelIntegrationId,
    val address: String,
)

/** Неразобранное сообщение чата. */
@Serializable
data class TriageMessageSchema(
    val id: InboundReceiptId,
    val body: String,
    /** Момент получения в формате ISO-8601 (UTC). */
    val receivedAt: String,
)

/** Неразобранные сообщения одного чата в порядке получения. */
@Serializable
data class TriageChatMessagesResponse(
    val messages: List<TriageMessageSchema>,
)

/** Ручная привязка чата [address] интеграции [integrationId] к клиенту [clientId]. */
@Serializable
data class AssignChatRequest(
    val integrationId: ChannelIntegrationId,
    val address: String,
    val clientId: ClientId,
)
