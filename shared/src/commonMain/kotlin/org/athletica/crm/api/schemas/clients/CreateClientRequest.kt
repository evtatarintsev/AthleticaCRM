package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CreateClientRequest(
    val id: Uuid,
    val name: String,
)
