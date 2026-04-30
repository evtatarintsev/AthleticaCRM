package org.athletica.crm.api.schemas.leadSources

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.LeadSourceId

@Serializable
data class DeleteLeadSourceRequest(
    val ids: List<LeadSourceId>,
)
