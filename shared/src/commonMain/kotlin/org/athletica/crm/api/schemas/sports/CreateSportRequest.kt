package org.athletica.crm.api.schemas.sports

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateSportRequest(
    val id: Uuid,
    val name: String,
)
