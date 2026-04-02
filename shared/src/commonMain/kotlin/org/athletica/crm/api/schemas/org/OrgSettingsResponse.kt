package org.athletica.crm.api.schemas.org

import kotlinx.serialization.Serializable

@Serializable
data class OrgSettingsResponse(
    val name: String,
    val timezone: String,
)
