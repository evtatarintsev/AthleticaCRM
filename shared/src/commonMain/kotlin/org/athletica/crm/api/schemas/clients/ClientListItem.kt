package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Краткая запись клиента в списке. */
@Serializable
data class ClientListItem(
    /** Уникальный идентификатор клиента. */
    val id: Uuid,
    /** Отображаемое имя клиента. */
    val name: String,
)

/** Ответ на запрос списка клиентов с поддержкой пагинации. */
@Serializable
data class ClientListResponse(
    /** Клиенты текущей страницы. */
    val clients: List<ClientListItem>,
    /** Общее количество клиентов в организации (без учёта пагинации). */
    val total: UInt,
)
