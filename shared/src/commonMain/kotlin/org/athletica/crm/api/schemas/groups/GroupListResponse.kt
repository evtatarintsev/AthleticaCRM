package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

@Serializable
data class GroupListResponse(
    val groups: List<GroupListItem>,
)

@Serializable
data class GroupListItem(
    /** Уникальный идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
)
