package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)
