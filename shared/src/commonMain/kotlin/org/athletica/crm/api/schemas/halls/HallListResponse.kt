package org.athletica.crm.api.schemas.halls

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.HallId

@Serializable
data class HallListResponse(
    val halls: List<HallDetailResponse>,
)

@Serializable
data class HallDetailResponse(
    val id: HallId,
    val name: String,
)
