package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Запрос на удаление списка клиентов из группы.
 * [clientIds] — идентификаторы клиентов, которых нужно удалить.
 * [groupId] — идентификатор группы.
 */
@Serializable
data class RemoveClientFromGroupRequest(
    /** Идентификаторы клиентов, удаляемых из группы. */
    val clientIds: List<Uuid>,
    /** Идентификатор группы, из которой удаляются клиенты. */
    val groupId: Uuid,
)
