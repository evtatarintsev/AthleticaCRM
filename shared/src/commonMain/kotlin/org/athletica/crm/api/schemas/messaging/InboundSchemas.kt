package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.InboundReceiptId

/** Неразобранный чат в очереди разбора: чат связанного канала, ещё не привязанный к клиенту. */
@Serializable
data class TriageChatSchema(
    /** Интеграция канала, в которой идёт чат. */
    val integrationId: ChannelIntegrationId,
    /** Отображаемое имя канала. */
    val channelName: String,
    /** Адрес чата в адресном пространстве канала. */
    val address: String,
    /** Отображаемое имя собеседника в канале; пустая строка, если неизвестно. */
    val displayName: String,
    /** Имя пользователя в канале без `@`; `null`, если не задано. */
    val username: String?,
    /** Номер телефона, присланный собеседником; `null`, если не присылал. */
    val phone: String?,
    /** Число неразобранных сообщений чата. */
    val unresolvedCount: Int,
    /** Текст последнего сообщения. */
    val lastMessageBody: String,
    /** Момент последнего сообщения в формате ISO-8601 (UTC). */
    val lastMessageAt: String,
)

/** Очередь ручного разбора: неразобранные чаты, новые первыми. */
@Serializable
data class TriageListResponse(
    val chats: List<TriageChatSchema>,
)

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
