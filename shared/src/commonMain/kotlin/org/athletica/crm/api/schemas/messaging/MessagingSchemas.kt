package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MessageId

/**
 * Сообщение в ленте диалога. Полиморфно по направлению (дискриминатор `type`):
 * исходящее несёт автора и состояние доставок, входящее — канал, по которому пришло.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface MessageSchema {
    val id: MessageId
    val body: String

    /** Момент создания в формате ISO-8601 (UTC). */
    val createdAt: String
}

/** Исходящее сообщение: автор (сотрудник или система) и состояние доставок по каналам. */
@Serializable
@SerialName("outbound")
data class OutboundMessageSchema(
    override val id: MessageId,
    override val body: String,
    override val createdAt: String,
    /** Сотрудник-автор; `null` — отправлено системой (автоматикой). */
    val authorEmployeeId: EmployeeId?,
    val deliveries: List<DeliverySchema>,
) : MessageSchema

/** Входящее сообщение от клиента: канал, по которому оно получено. */
@Serializable
@SerialName("inbound")
data class InboundMessageSchema(
    override val id: MessageId,
    override val body: String,
    override val createdAt: String,
    val receivedVia: ChannelIntegrationId,
) : MessageSchema

/** Состояние доставки исходящего сообщения в конкретный канал. */
@Serializable
data class DeliverySchema(
    val channelIntegrationId: ChannelIntegrationId,
    val state: DeliveryStateSchema,
    /** Текст ошибки; присутствует только при [DeliveryStateSchema.FAILED]. */
    val errorMessage: String?,
)

/** Состояние доставки для UI. */
@Serializable
enum class DeliveryStateSchema {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
}

/** Лента диалога с клиентом: сообщения по всем каналам, от старых к новым. */
@Serializable
data class ConversationResponse(
    val clientId: ClientId,
    val messages: List<MessageSchema>,
    /** Контакты клиента: определяют доступность каналов и выбор адреса при отправке. */
    val contacts: List<ClientContactSchema> = emptyList(),
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
    /**
     * Контакт-адресат: какой из подходящих под канал контактов использовать.
     * `null` — взять первый подходящий (поведение по умолчанию).
     */
    val contactId: ClientContactId? = null,
)
