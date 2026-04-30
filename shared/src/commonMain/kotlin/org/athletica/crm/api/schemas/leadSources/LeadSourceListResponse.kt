package org.athletica.crm.api.schemas.leadSources

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.LeadSourceId

@Serializable
data class LeadSourceListResponse(
    val leadSources: List<LeadSourceDetailResponse>,
)

@Serializable
data class LeadSourceDetailResponse(
    val id: LeadSourceId,
    val name: String,
)
