package org.athletica.crm.api.schemas.leadSources

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.LeadSourceId

@Serializable
data class UpdateLeadSourceRequest(
    val id: LeadSourceId,
    val name: String,
    /** Пояснение: что относится к источнику; пустая строка — без пояснения. */
    val description: String = "",
)
