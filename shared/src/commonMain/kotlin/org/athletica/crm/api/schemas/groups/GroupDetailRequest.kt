package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

/** Запрос детальной информации о группе по [id]. */
@Serializable
data class GroupDetailRequest(
    val id: GroupId,
)
