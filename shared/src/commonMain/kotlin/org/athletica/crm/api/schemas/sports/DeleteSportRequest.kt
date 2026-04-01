package org.athletica.crm.api.schemas.sports

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class DeleteSportRequest(
    val ids: List<Uuid>,
)
