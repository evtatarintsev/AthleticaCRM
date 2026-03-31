package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable

@Serializable
data class GroupListRequest(
    val name: String? = null,
)
