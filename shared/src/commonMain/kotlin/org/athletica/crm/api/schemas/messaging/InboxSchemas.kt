package org.athletica.crm.api.schemas.messaging

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId

/** Фильтр списка «Входящих». */
@Serializable
enum class InboxFilterSchema {
    /** Все беседы клиентов и неразобранные чаты. */
    ALL,

    /** Беседы клиентов с непрочитанными входящими и все неразобранные чаты. */
    UNREAD,

    /** Только неразобранные чаты. */
    UNRESOLVED,
}

/** Запрос страницы списка «Входящих». */
@Serializable
data class InboxListRequest(
    /** Фильтр списка. */
    val filter: InboxFilterSchema = InboxFilterSchema.ALL,
    /** Максимальное количество записей на странице. Сервер ограничивает значение сверху. */
    val limit: Int = 50,
    /** Смещение от начала отфильтрованной выборки (для пагинации). */
    val offset: Int = 0,
)

/** Направление последнего сообщения беседы относительно организации. */
@Serializable
enum class InboxMessageDirectionSchema {
    /** Сообщение клиента. */
    INBOUND,

    /** Сообщение сотрудника или системы. */
    OUTBOUND,
}

/**
 * Запись списка «Входящих». Полиморфна по виду (дискриминатор `type`):
 * беседа клиента или неразобранный чат.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface InboxItemSchema {
    /** Текст последнего сообщения. */
    val lastMessageBody: String

    /** Момент последнего сообщения в формате ISO-8601 (UTC). */
    val lastMessageAt: String
}

/** Беседа с клиентом во «Входящих». */
@Serializable
@SerialName("client")
data class ClientConversationItemSchema(
    /** Клиент беседы. */
    val clientId: ClientId,
    /** Имя клиента. */
    val clientName: String,
    override val lastMessageBody: String,
    override val lastMessageAt: String,
    /** Направление последнего сообщения. */
    val lastMessageDirection: InboxMessageDirectionSchema,
    /** Число входящих сообщений после момента прочтения беседы. */
    val unreadCount: Int,
) : InboxItemSchema

/** Неразобранный чат связанного канала: ещё не привязан к клиенту. */
@Serializable
@SerialName("unresolved")
data class UnresolvedChatItemSchema(
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
    override val lastMessageBody: String,
    override val lastMessageAt: String,
) : InboxItemSchema

/** Страница списка «Входящих»: свежие первыми. */
@Serializable
data class InboxListResponse(
    /** Записи текущей страницы. */
    val items: List<InboxItemSchema>,
    /** Общее количество записей под текущим фильтром (без учёта пагинации). */
    val total: Int,
)

/** Число записей «Входящих», требующих внимания: беседы с непрочитанным плюс неразобранные чаты. */
@Serializable
data class InboxCounterResponse(
    val count: Int,
)

/** Отметка прочтения беседы с клиентом [clientId] до момента [upTo]. */
@Serializable
data class MarkConversationReadRequest(
    val clientId: ClientId,
    /** Момент последнего показанного сотруднику сообщения в формате ISO-8601 (UTC). */
    val upTo: String,
)
