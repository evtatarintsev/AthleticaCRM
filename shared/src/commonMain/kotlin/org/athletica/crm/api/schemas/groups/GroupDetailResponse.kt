package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Полные данные группы, возвращаемые после создания или запроса деталей. */
@Serializable
data class GroupDetailResponse(
    /** Уникальный идентификатор группы. */
    val id: Uuid,
    /** Название группы. */
    val name: String,
)
