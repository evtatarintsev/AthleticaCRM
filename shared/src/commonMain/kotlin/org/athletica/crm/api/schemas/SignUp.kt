package org.athletica.crm.api.schemas

import kotlinx.serialization.Serializable

@Serializable
data class SignUpRequest(
    val companyName: String,
    val userName: String,
    val login: String,
    val password: String
)
