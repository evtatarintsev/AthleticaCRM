package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable
import org.athletica.crm.core.money.Currency

@Serializable
data class SignUpRequest(
    val companyName: String,
    val userName: String,
    val login: String,
    val password: String,
    val timezone: String,
    /**
     * Валюта, в которой организация будет вести все денежные операции.
     * Фиксируется на регистрации, в дальнейшем не меняется.
     */
    val currency: Currency,
)
