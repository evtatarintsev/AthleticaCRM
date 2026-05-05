package org.athletica.crm.api.schemas.customfields

import kotlinx.serialization.Serializable

@Serializable
data class CustomFieldsListRequest(
    val entityType: String,
)
