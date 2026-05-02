package org.athletica.crm.api.schemas.halls

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.HallId

@Serializable
data class UpdateHallRequest(
    val id: HallId,
    val name: String,
)
