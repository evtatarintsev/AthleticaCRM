package org.athletica.crm.api.schemas.sports

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SportListResponse(
    val sports: List<SportDetailResponse>,
)

@Serializable
data class SportDetailResponse(
    val id: Uuid,
    val name: String,
)
