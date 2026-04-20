package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на удаление списка клиентов из группы.
 * [clientIds] — идентификаторы клиентов, которых нужно удалить.
 * [groupId] — идентификатор группы.
 */
@Serializable
data class RemoveClientFromGroupRequest(
    /** Идентификаторы клиентов, удаляемых из группы. */
    val clientIds: List<ClientId>,
    /** Идентификатор группы, из которой удаляются клиенты. */
    val groupId: GroupId,
)
