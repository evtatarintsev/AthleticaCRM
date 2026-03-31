package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class GroupListResponse(
    val groups: List<GroupListItem>,
)

@Serializable
data class GroupListItem(
    /** Уникальный идентификатор группы. */
    val id: Uuid,
    /** Название группы. */
    val name: String,
)
