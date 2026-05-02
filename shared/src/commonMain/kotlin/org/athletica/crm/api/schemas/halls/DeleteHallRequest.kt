package org.athletica.crm.api.schemas.halls

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.HallId

@Serializable
data class DeleteHallRequest(
    val ids: List<HallId>,
)
