package org.athletica.crm.api.schemas.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmEmailRequest(val token: String)

@Serializable
enum class EmailConfirmationStatus {
    @SerialName("confirmed")
    CONFIRMED,

    @SerialName("already_confirmed")
    ALREADY_CONFIRMED,
}

@Serializable
data class ConfirmEmailResponse(val status: EmailConfirmationStatus)

@Serializable
enum class ResendEmailStatus {
    @SerialName("queued")
    QUEUED,

    @SerialName("already_confirmed")
    ALREADY_CONFIRMED,

    @SerialName("rate_limited")
    RATE_LIMITED,
}

@Serializable
data class ResendEmailConfirmationResponse(
    val status: ResendEmailStatus,
    val retryAfterSeconds: Int,
)
