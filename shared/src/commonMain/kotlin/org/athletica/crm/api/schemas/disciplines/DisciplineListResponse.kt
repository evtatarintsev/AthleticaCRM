package org.athletica.crm.api.schemas.disciplines

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DisciplineListResponse(
    val disciplines: List<DisciplineDetailResponse>,
)

@Serializable
data class DisciplineDetailResponse(
    val id: Uuid,
    val name: String,
)
