package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на запись одного клиента сразу в несколько групп одной операцией:
 * клиент записывается либо во все группы, либо ни в одну.
 */
@Serializable
data class AddClientToGroupsRequest(
    /** Идентификатор клиента, которого записывают в группы. */
    val clientId: ClientId,
    /** Идентификаторы групп, в которые записывается клиент. */
    val groupIds: List<GroupId>,
)
