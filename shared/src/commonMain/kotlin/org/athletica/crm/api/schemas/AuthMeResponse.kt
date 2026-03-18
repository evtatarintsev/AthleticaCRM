package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

@Serializable
data class AuthMeResponse(
    val id: Int,
    val username: String,
)
