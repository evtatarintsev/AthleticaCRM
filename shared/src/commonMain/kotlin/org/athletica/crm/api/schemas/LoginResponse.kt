package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
