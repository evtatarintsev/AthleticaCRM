package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.Serializable
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.money.Currency

@Serializable
data class SignUpRequest(
    val companyName: NonBlankName,
    val userName: NonBlankName,
    val login: String,
    val password: String,
    val timezone: String,
    /**
     * Валюта, в которой организация будет вести все денежные операции.
     * Фиксируется на регистрации, в дальнейшем не меняется.
     */
    val currency: Currency,
)
