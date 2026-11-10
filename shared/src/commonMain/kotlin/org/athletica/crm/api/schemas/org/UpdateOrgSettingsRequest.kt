package org.athletica.crm.api.schemas.org

import kotlinx.serialization.Serializable
import org.athletica.crm.core.NonBlankName

@Serializable
data class UpdateOrgSettingsRequest(
    val name: NonBlankName,
    val timezone: String,
)
