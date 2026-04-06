package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Запрос на добавление списка клиентов в группу.
 * [clientIds] — идентификаторы клиентов, которых нужно добавить.
 * [groupId] — идентификатор целевой группы.
 */
@Serializable
data class AddClientsToGroupRequest(
    /** Идентификаторы клиентов, добавляемых в группу. */
    val clientIds: List<Uuid>,
    /** Идентификатор группы, в которую добавляются клиенты. */
    val groupId: Uuid,
)
