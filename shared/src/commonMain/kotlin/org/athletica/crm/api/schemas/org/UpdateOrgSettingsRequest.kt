package org.athletica.crm.api.schemas.org

import kotlinx.serialization.Serializable

@Serializable
data class UpdateOrgSettingsRequest(
    val name: String,
    val timezone: String,
)
