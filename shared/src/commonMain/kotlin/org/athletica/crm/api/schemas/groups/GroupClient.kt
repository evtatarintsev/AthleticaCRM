package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/**
 * Клиент, записанный в группу (постоянный участник).
 * [id] — идентификатор клиента, [name] — отображаемое имя.
 */
@Serializable
data class GroupClient(
    val id: ClientId,
    val name: String,
)
